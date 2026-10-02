package com.edumentor.booking.service;

import com.edumentor.booking.config.BookingProperties;
import com.edumentor.booking.dto.BookingResponse;
import com.edumentor.booking.dto.PageResponse;
import com.edumentor.booking.entity.Booking;
import com.edumentor.booking.entity.BookingStatus;
import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.SlotStatus;
import com.edumentor.booking.exception.ApiException;
import com.edumentor.booking.repository.BookingRepository;
import com.edumentor.booking.repository.MentorSlotRepository;
import com.edumentor.booking.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MentorSlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final MentorDirectory mentorDirectory;
    private final BookingOutboxService outbox;
    private final TransactionTemplate transactionTemplate;
    private final BookingProperties properties;
    private final Clock clock;

    private record Locked(Booking booking, MentorSlot slot) {
    }

    // ---------- Student: hold a slot ----------

    /**
     * Not annotated @Transactional on purpose: the remote mentor check runs first, with no database
     * connection or row lock held, and only the short hold itself runs inside a transaction.
     */
    public BookingResponse createBooking(Long studentId, Long slotId, String authorization) {
        MentorSlot preview = slotRepository.findById(slotId).orElseThrow(ApiException::slotNotFound);
        mentorDirectory.requireApprovedMentor(preview.getMentorId(), authorization);

        try {
            Booking booking = Objects.requireNonNull(
                    transactionTemplate.execute(status -> holdSlot(studentId, slotId)));
            log.info("Slot held: bookingId={}, slotId={}, studentId={}", booking.getId(), slotId, studentId);
            return BookingResponse.from(booking);
        } catch (DataIntegrityViolationException ex) {
            // The unique active_slot_id constraint is the last line of defence
            log.warn("Unique constraint stopped a double booking: slotId={}", slotId);
            throw ApiException.slotNotAvailable();
        }
    }

    private Booking holdSlot(Long studentId, Long slotId) {
        Instant now = Instant.now(clock);
        // Locking read: concurrent holds for this slot are serialized here
        MentorSlot slot = slotRepository.findByIdForUpdate(slotId).orElseThrow(ApiException::slotNotFound);

        if (slot.getStatus() == SlotStatus.HELD && slot.getHoldExpiresAt() != null
                && !slot.getHoldExpiresAt().isAfter(now)) {
            releaseExpiredHold(slot);
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw ApiException.slotNotAvailable();
        }
        if (!slot.getStartTime().isAfter(now.plus(properties.holdDuration()))) {
            throw ApiException.slotTooSoon();
        }
        if (bookingRepository.countByStudentIdAndStatus(studentId, BookingStatus.PENDING_PAYMENT)
                >= properties.maxPendingPerStudent()) {
            throw ApiException.tooManyPendingBookings();
        }

        Instant expiresAt = now.plus(properties.holdDuration());
        slot.hold(studentId, expiresAt);

        Booking booking = new Booking();
        booking.setSlotId(slot.getId());
        booking.setStudentId(studentId);
        booking.setMentorId(slot.getMentorId());
        booking.setMentorUserId(slot.getMentorUserId());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setPrice(slot.getPrice());
        booking.setSessionStart(slot.getStartTime());
        booking.setSessionEnd(slot.getEndTime());
        booking.setHoldExpiresAt(expiresAt);
        booking.setActiveSlotId(slot.getId());
        Booking saved = bookingRepository.saveAndFlush(booking);
        outbox.bookingCreated(saved);
        return saved;
    }

    /** Frees a slot whose hold ran out before the scheduler got to it. Caller holds the slot lock. */
    private void releaseExpiredHold(MentorSlot slot) {
        bookingRepository.findByActiveSlotId(slot.getId()).ifPresent(existing -> {
            if (existing.getStatus() == BookingStatus.PENDING_PAYMENT) {
                existing.closeAs(BookingStatus.EXPIRED, "Payment window expired");
                outbox.bookingCancelled(existing);
                log.info("Expired hold released inline: bookingId={}, slotId={}", existing.getId(), slot.getId());
            }
        });
        slot.release();
        // Hibernate runs INSERTs before UPDATEs; flush so the old booking frees active_slot_id first
        bookingRepository.flush();
    }

    // ---------- Student: cancel an unpaid hold ----------

    @Transactional
    public BookingResponse cancelByStudent(Long studentId, Long bookingId) {
        Locked locked = lockBookingWithSlot(bookingId);
        Booking booking = locked.booking();
        if (!booking.getStudentId().equals(studentId)) {
            throw ApiException.bookingNotFound();
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return BookingResponse.from(booking);
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.bookingNotCancellable();
        }
        closeAndRelease(locked, BookingStatus.CANCELLED, "Cancelled by student");
        log.info("Booking cancelled by student: bookingId={}", bookingId);
        return BookingResponse.from(booking);
    }

    // ---------- Mentor: complete a session ----------

    @Transactional
    public BookingResponse completeByMentor(Long mentorUserId, Long bookingId) {
        Locked locked = lockBookingWithSlot(bookingId);
        Booking booking = locked.booking();
        if (!booking.getMentorUserId().equals(mentorUserId)) {
            throw ApiException.bookingNotFound();
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            return BookingResponse.from(booking);
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.invalidBookingState("Only CONFIRMED bookings can be completed");
        }
        Instant now = Instant.now(clock);
        if (now.isBefore(booking.getSessionStart())) {
            throw ApiException.sessionNotStarted();
        }
        booking.markCompleted(now);
        log.info("Booking completed: bookingId={}", bookingId);
        return BookingResponse.from(booking);
    }

    // ---------- Saga steps (called by PaymentEventHandler) ----------

    /**
     * Payment succeeded. Idempotent for the same payment id. A booking that is no longer
     * PENDING_PAYMENT (cancelled or expired) is rejected so the caller can trigger a refund.
     * A hold that has expired but has not been swept yet is still honoured: the slot is still ours.
     */
    @Transactional
    public BookingResponse confirmPayment(Long bookingId, Long paymentId) {
        Locked locked = lockBookingWithSlot(bookingId);
        Booking booking = locked.booking();
        if (booking.getStatus() == BookingStatus.CONFIRMED && Objects.equals(booking.getPaymentId(), paymentId)) {
            return BookingResponse.from(booking);
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.bookingNotPending();
        }
        if (locked.slot().getStatus() != SlotStatus.HELD) {
            throw ApiException.invalidBookingState("The slot is not held for this booking");
        }
        locked.slot().markBooked();
        booking.markConfirmed(paymentId, Instant.now(clock));
        log.info("Booking confirmed: bookingId={}, paymentId={}", bookingId, paymentId);
        return BookingResponse.from(booking);
    }

    /** Payment failed: cancel the booking and release the slot. Idempotent. */
    @Transactional
    public BookingResponse cancelAfterPaymentFailure(Long bookingId, String reason) {
        Locked locked = lockBookingWithSlot(bookingId);
        Booking booking = locked.booking();
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            return BookingResponse.from(booking);
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.invalidBookingState("Only bookings awaiting payment can be cancelled");
        }
        closeAndRelease(locked, BookingStatus.CANCELLED, reason == null ? "Payment failed" : reason);
        log.info("Booking cancelled after payment failure: bookingId={}", bookingId);
        return BookingResponse.from(booking);
    }

    /** Asks payment-service (through an event) to refund a payment this booking cannot honour. */
    @Transactional
    public void rejectPayment(Long bookingId, Long paymentId, Long studentId, String reason) {
        outbox.paymentRejected(bookingId, paymentId, studentId, reason);
        log.warn("Payment rejected, refund requested: bookingId={}, paymentId={}", bookingId, paymentId);
    }

    @Transactional(readOnly = true)
    public boolean isConfirmationAnnounced(Long bookingId) {
        return bookingRepository.findById(bookingId).map(Booking::isConfirmationAnnounced).orElse(true);
    }

    /** Stores the meeting link and queues BOOKING_CONFIRMED exactly once. */
    @Transactional
    public void announceConfirmation(Long bookingId, String meetingUrl) {
        Locked locked = lockBookingWithSlot(bookingId);
        Booking booking = locked.booking();
        boolean announceable = booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.COMPLETED;
        if (booking.isConfirmationAnnounced() || !announceable) {
            return;
        }
        booking.setMeetingUrl(meetingUrl);
        booking.setConfirmationAnnounced(true);
        outbox.bookingConfirmed(booking, meetingUrl);
    }

    // ---------- Hold expiry (called by HoldExpiryService) ----------

    /** Expires a single stale hold. Returns false if the booking was paid or cancelled in the meantime. */
    @Transactional
    public boolean expireHold(Long bookingId) {
        Locked locked = lockBookingWithSlot(bookingId);
        Booking booking = locked.booking();
        Instant now = Instant.now(clock);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT
                || booking.getHoldExpiresAt() == null || booking.getHoldExpiresAt().isAfter(now)) {
            return false;
        }
        closeAndRelease(locked, BookingStatus.EXPIRED, "Payment window expired");
        log.info("Hold expired: bookingId={}, slotId={}", bookingId, booking.getSlotId());
        return true;
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public BookingResponse getBooking(AuthenticatedUser user, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(ApiException::bookingNotFound);
        boolean allowed = "ADMIN".equals(user.role())
                || booking.getStudentId().equals(user.userId())
                || booking.getMentorUserId().equals(user.userId());
        if (!allowed) {
            throw ApiException.bookingNotFound();
        }
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listMine(AuthenticatedUser user, BookingStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        boolean mentor = "MENTOR".equals(user.role());
        Page<Booking> result;
        if (mentor) {
            result = status == null
                    ? bookingRepository.findByMentorUserId(user.userId(), pageable)
                    : bookingRepository.findByMentorUserIdAndStatus(user.userId(), status, pageable);
        } else {
            result = status == null
                    ? bookingRepository.findByStudentId(user.userId(), pageable)
                    : bookingRepository.findByStudentIdAndStatus(user.userId(), status, pageable);
        }
        return PageResponse.from(result, BookingResponse::from);
    }

    // ---------- helpers ----------

    /**
     * Lock order is always slot first, then booking, so concurrent operations cannot deadlock.
     * Only locking reads are used afterwards: under MySQL REPEATABLE READ a plain read would see a
     * snapshot taken before we waited for the lock.
     */
    private Locked lockBookingWithSlot(Long bookingId) {
        Long slotId = bookingRepository.findSlotIdById(bookingId).orElseThrow(ApiException::bookingNotFound);
        MentorSlot slot = slotRepository.findByIdForUpdate(slotId).orElseThrow(ApiException::slotNotFound);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow(ApiException::bookingNotFound);
        return new Locked(booking, slot);
    }

    private void closeAndRelease(Locked locked, BookingStatus terminalStatus, String reason) {
        locked.booking().closeAs(terminalStatus, reason);
        outbox.bookingCancelled(locked.booking());
        if (locked.slot().getStatus() == SlotStatus.HELD) {
            locked.slot().release();
        }
    }
}