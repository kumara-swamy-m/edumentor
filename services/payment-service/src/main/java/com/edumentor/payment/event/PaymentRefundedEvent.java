package com.edumentor.payment.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRefundedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        Long paymentId,
        Long bookingId,
        Long studentId,
        BigDecimal amount,
        String currency,
        String reason
) {
}