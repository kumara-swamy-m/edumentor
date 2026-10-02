package com.edumentor.payment.dto;

/** {@code replayed} is true when an existing payment was returned for a repeated Idempotency-Key. */
public record CreatePaymentResult(PaymentResponse payment, boolean replayed) {
}