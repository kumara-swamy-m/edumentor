package com.edumentor.payment.provider;

import java.math.BigDecimal;

public record CreateProviderPayment(Long paymentId, Long bookingId, Long studentId, BigDecimal amount,
                                    String currency) {

    /** Stable per payment, so retrying a half-finished create never charges twice. */
    public String idempotencyKey() {
        return "edumentor-payment-" + paymentId;
    }
}