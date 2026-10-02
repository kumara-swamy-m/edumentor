package com.edumentor.payment.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Strongly typed Kafka payload (published by the Phase 6 outbox relay). */
public record PaymentSuccessEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        Long paymentId,
        Long bookingId,
        Long studentId,
        BigDecimal amount,
        String currency
) {
}