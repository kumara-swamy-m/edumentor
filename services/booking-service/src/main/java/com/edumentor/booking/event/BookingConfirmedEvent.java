package com.edumentor.booking.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingConfirmedEvent(
        String eventId, String eventType, Instant occurredAt, String correlationId,
        Long bookingId, Long studentId, Long mentorId, Long mentorUserId,
        Instant sessionStart, Instant sessionEnd, Long paymentId, BigDecimal amount, String currency,
        String meetingUrl) {
}