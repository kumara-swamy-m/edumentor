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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_booking", columnNames = "booking_id"),
                @UniqueConstraint(name = "uk_payment_student_idem", columnNames = {"student_id", "idempotency_key"}),
                @UniqueConstraint(name = "uk_payment_provider_pid", columnNames = {"provider", "provider_payment_id"})
        },
        indexes = {
                @Index(name = "idx_payment_student", columnList = "student_id"),
                @Index(name = "idx_payment_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** booking-service booking id (no cross-database foreign key). */
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** auth-service user id of the paying student. */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    /** Returned to the owner while PENDING so an idempotent replay can resume checkout. Never logged. */
    @Column(name = "client_secret", length = 255)
    private String clientSecret;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}