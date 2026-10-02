package com.edumentor.payment.dto;

import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long bookingId,
        Long studentId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String providerPaymentId,
        String clientSecret,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {

    /** The client secret is exposed only to the owner and only while the payment can still be completed. */
    public static PaymentResponse from(Payment p, boolean includeClientSecret) {
        String secret = includeClientSecret && p.getStatus() == PaymentStatus.PENDING ? p.getClientSecret() : null;
        return new PaymentResponse(p.getId(), p.getBookingId(), p.getStudentId(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getProvider(), p.getProviderPaymentId(), secret, p.getFailureReason(),
                p.getCreatedAt(), p.getCompletedAt());
    }
}