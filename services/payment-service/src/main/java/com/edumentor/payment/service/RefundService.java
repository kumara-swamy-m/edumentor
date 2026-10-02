package com.edumentor.payment.service;

import com.edumentor.payment.entity.OutboxEventType;
import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.entity.PaymentStatus;
import com.edumentor.payment.entity.PaymentTransaction;
import com.edumentor.payment.entity.TransactionType;
import com.edumentor.payment.provider.PaymentProvider;
import com.edumentor.payment.repository.PaymentRepository;
import com.edumentor.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentProvider provider;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    /** Idempotent. ProviderException is allowed to propagate so the Kafka consumer retries. */
    public void refundRejectedPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("Refund requested for unknown payment: paymentId={}", paymentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Refund skipped, already refunded: paymentId={}", paymentId);
            return;
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            log.warn("Refund skipped, payment is {}: paymentId={}", payment.getStatus(), paymentId);
            return;
        }

        provider.refund(payment.getProviderPaymentId(), "edumentor-refund-" + paymentId);

        transactionTemplate.executeWithoutResult(status -> {
            Payment locked = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
            if (locked.getStatus() != PaymentStatus.SUCCESS) {
                return;
            }
            locked.setStatus(PaymentStatus.REFUNDED);
            transactionRepository.save(new PaymentTransaction(locked.getId(), TransactionType.PAYMENT_REFUNDED,
                    PaymentStatus.REFUNDED, "BOOKING_EVENT", null, reason));
            outboxService.record(OutboxEventType.PAYMENT_REFUNDED, locked, reason);
            log.info("Payment refunded: paymentId={}, bookingId={}", locked.getId(), locked.getBookingId());
        });
    }
}