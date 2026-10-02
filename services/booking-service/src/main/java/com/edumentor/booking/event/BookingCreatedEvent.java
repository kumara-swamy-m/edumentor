package com.edumentor.booking.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingCreatedEvent(
        String eventId, String eventType, Instant occurredAt, String correlationId,
        Long bookingId, Long slotId, Long studentId, Long mentorId, Long mentorUserId,
        Instant sessionStart, Instant sessionEnd, BigDecimal amount, String currency, Instant holdExpiresAt) {
}