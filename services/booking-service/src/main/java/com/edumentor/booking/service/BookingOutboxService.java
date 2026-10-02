package com.edumentor.booking.service;

import com.edumentor.booking.entity.Booking;
import com.edumentor.booking.entity.OutboxEvent;
import com.edumentor.booking.entity.OutboxEventType;
import com.edumentor.booking.event.BookingCancelledEvent;
import com.edumentor.booking.event.BookingConfirmedEvent;
import com.edumentor.booking.event.BookingCreatedEvent;
import com.edumentor.booking.event.BookingPaymentRejectedEvent;
import com.edumentor.booking.event.SessionReminderEvent;
import com.edumentor.booking.repository.OutboxEventRepository;
import com.edumentor.booking.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Every method must run inside the caller's transaction, next to the state change it describes. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BookingOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void bookingCreated(Booking b) {
        String id = newId();
        store(OutboxEventType.BOOKING_CREATED, b.getId(), id, new BookingCreatedEvent(id,
                OutboxEventType.BOOKING_CREATED.name(), now(), correlationId(), b.getId(), b.getSlotId(),
                b.getStudentId(), b.getMentorId(), b.getMentorUserId(), b.getSessionStart(), b.getSessionEnd(),
                b.getPrice(), b.getCurrency(), b.getHoldExpiresAt()));
    }

    public void bookingConfirmed(Booking b, String meetingUrl) {
        String id = newId();
        store(OutboxEventType.BOOKING_CONFIRMED, b.getId(), id, new BookingConfirmedEvent(id,
                OutboxEventType.BOOKING_CONFIRMED.name(), now(), correlationId(), b.getId(), b.getStudentId(),
                b.getMentorId(), b.getMentorUserId(), b.getSessionStart(), b.getSessionEnd(), b.getPaymentId(),
                b.getPrice(), b.getCurrency(), meetingUrl));
    }

    public void bookingCancelled(Booking b) {
        String id = newId();
        store(OutboxEventType.BOOKING_CANCELLED, b.getId(), id, new BookingCancelledEvent(id,
                OutboxEventType.BOOKING_CANCELLED.name(), now(), correlationId(), b.getId(), b.getStudentId(),
                b.getMentorUserId(), b.getStatus().name(), b.getCancelReason()));
    }

    public void paymentRejected(Long bookingId, Long paymentId, Long studentId, String reason) {
        String id = newId();
        store(OutboxEventType.BOOKING_PAYMENT_REJECTED, bookingId, id, new BookingPaymentRejectedEvent(id,
                OutboxEventType.BOOKING_PAYMENT_REJECTED.name(), now(), correlationId(), bookingId, paymentId,
                studentId, reason));
    }

    public void sessionReminder(Booking b) {
        String id = newId();
        store(OutboxEventType.SESSION_REMINDER, b.getId(), id, new SessionReminderEvent(id,
                OutboxEventType.SESSION_REMINDER.name(), now(), correlationId(), b.getId(), b.getStudentId(),
                b.getMentorUserId(), b.getSessionStart(), b.getSessionEnd(), b.getMeetingUrl()));
    }

    private void store(OutboxEventType type, Long bookingId, String eventId, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("BOOKING");
        event.setAggregateId(String.valueOf(bookingId));
        event.setMessageKey(String.valueOf(bookingId));
        event.setEventType(type);
        event.setCorrelationId(correlationId());
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize outbox event", ex);
        }
        outboxEventRepository.save(event);
        log.info("Outbox event recorded: type={}, bookingId={}, eventId={}", type, bookingId, eventId);
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}