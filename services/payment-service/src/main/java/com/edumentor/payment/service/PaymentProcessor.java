package com.edumentor.payment.service;

import com.edumentor.payment.entity.OutboxEventType;
import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.entity.PaymentStatus;
import com.edumentor.payment.entity.PaymentTransaction;
import com.edumentor.payment.entity.TransactionType;
import com.edumentor.payment.provider.ProviderOutcome;
import com.edumentor.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentTransactionRepository transactionRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    /**
     * Applies a provider outcome. Idempotent: repeating an outcome that is already reflected changes nothing.
     * Returns true when the state actually changed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(Payment payment, ProviderOutcome outcome, String source, String providerEventId,
                         String reason) {
        Instant now = Instant.now(clock);
        return switch (outcome) {
            case SUCCEEDED -> applySuccess(payment, now, source, providerEventId);
            case FAILED -> applyFailure(payment, now, source, providerEventId, reason);
            case PENDING, IGNORED -> false;
        };
    }

    private boolean applySuccess(Payment payment, Instant now, String source, String providerEventId) {
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Success outcome ignored, payment already {}: paymentId={}", payment.getStatus(),
                    payment.getId());
            return false;
        }
        // A provider can succeed after an earlier failed attempt (customer retried): the money did move
        boolean afterFailure = payment.getStatus() == PaymentStatus.FAILED;
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCompletedAt(now);
        payment.setFailureReason(null);
        transactionRepository.save(new PaymentTransaction(payment.getId(), TransactionType.PAYMENT_SUCCEEDED,
                PaymentStatus.SUCCESS, source, providerEventId,
                afterFailure ? "Succeeded after an earlier failed attempt" : "Payment succeeded"));
        outboxService.record(OutboxEventType.PAYMENT_SUCCESS, payment, null);
        log.info("Payment confirmed: paymentId={}, bookingId={}, source={}", payment.getId(),
                payment.getBookingId(), source);
        return true;
    }

    private boolean applyFailure(Payment payment, Instant now, String source, String providerEventId,
                                 String reason) {
        if (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Failure outcome ignored, payment already {}: paymentId={}", payment.getStatus(),
                    payment.getId());
            return false;
        }
        String failureReason = reason == null || reason.isBlank() ? "Payment failed" : reason;
        failureReason = failureReason.substring(0, Math.min(failureReason.length(), 200));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setCompletedAt(now);
        payment.setFailureReason(failureReason);
        transactionRepository.save(new PaymentTransaction(payment.getId(), TransactionType.PAYMENT_FAILED,
                PaymentStatus.FAILED, source, providerEventId, failureReason));
        outboxService.record(OutboxEventType.PAYMENT_FAILED, payment, failureReason);
        log.info("Payment failed: paymentId={}, bookingId={}, source={}", payment.getId(),
                payment.getBookingId(), source);
        return true;
    }
}