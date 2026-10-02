package com.edumentor.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_transactions",
        indexes = @Index(name = "idx_ptx_payment", columnList = "payment_id"))
@Getter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status", nullable = false, length = 20)
    private PaymentStatus resultingStatus;

    /** API, WEBHOOK or VERIFY. */
    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "provider_event_id", length = 100)
    private String providerEventId;

    @Column(length = 200)
    private String message;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentTransaction(Long paymentId, TransactionType type, PaymentStatus resultingStatus, String source,
                              String providerEventId, String message) {
        this.paymentId = paymentId;
        this.type = type;
        this.resultingStatus = resultingStatus;
        this.source = source;
        this.providerEventId = providerEventId;
        this.message = message == null ? null : message.substring(0, Math.min(message.length(), 200));
    }
}