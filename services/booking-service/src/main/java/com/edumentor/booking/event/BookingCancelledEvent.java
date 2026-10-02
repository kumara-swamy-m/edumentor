package com.edumentor.booking.event;

import java.time.Instant;

/** {@code status} is CANCELLED or EXPIRED. */
public record BookingCancelledEvent(
        String eventId, String eventType, Instant occurredAt, String correlationId,
        Long bookingId, Long studentId, Long mentorUserId, String status, String reason) {
}