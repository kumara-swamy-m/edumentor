package com.edumentor.booking.service;

import com.edumentor.booking.entity.Booking;
import com.edumentor.booking.entity.BookingStatus;
import com.edumentor.booking.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class ReminderService {

    private final BookingRepository bookingRepository;
    private final BookingOutboxService outbox;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration lead;

    public ReminderService(BookingRepository bookingRepository, BookingOutboxService outbox,
                           TransactionTemplate transactionTemplate, Clock clock,
                           @Value("${app.booking.reminder-lead:PT1H}") Duration lead) {
        this.bookingRepository = bookingRepository;
        this.outbox = outbox;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.lead = lead;
    }

    /** Queues one SESSION_REMINDER for each confirmed session starting within the lead time. */
    public int sendDueReminders() {
        Instant now = Instant.now(clock);
        List<Long> ids = bookingRepository.findReminderCandidateIds(BookingStatus.CONFIRMED, now,
                now.plus(lead), PageRequest.of(0, 100));
        int queued = 0;
        for (Long id : ids) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> markAndQueue(id, now)))) {
                    queued++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not queue reminder: bookingId={}, cause={}", id, ex.getClass().getSimpleName());
            }
        }
        return queued;
    }

    private boolean markAndQueue(Long id, Instant now) {
        Booking booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED || booking.isReminderSent()
                || !booking.getSessionStart().isAfter(now)) {
            return false;
        }
        booking.setReminderSent(true);
        outbox.sessionReminder(booking);
        return true;
    }
}