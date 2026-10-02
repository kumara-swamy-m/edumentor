package com.edumentor.booking.event;

import java.time.Instant;

/** A payment succeeded but the booking could not be honoured: payment-service must refund it. */
public record BookingPaymentRejectedEvent(
        String eventId, String eventType, Instant occurredAt, String correlationId,
        Long bookingId, Long paymentId, Long studentId, String reason) {
}