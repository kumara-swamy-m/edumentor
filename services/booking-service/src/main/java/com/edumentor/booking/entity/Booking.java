package com.edumentor.booking.entity;

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
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_active_slot", columnNames = "active_slot_id"),
        indexes = {
                @Index(name = "idx_booking_student", columnList = "student_id"),
                @Index(name = "idx_booking_mentor_user", columnList = "mentor_user_id"),
                @Index(name = "idx_booking_status_hold", columnList = "status,hold_expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "session_start", nullable = false)
    private Instant sessionStart;

    @Column(name = "session_end", nullable = false)
    private Instant sessionEnd;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    /** payment-service payment id (set when payment succeeds). */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    /** Slot id while this booking is active, NULL otherwise. Unique: one active booking per slot. */
    @Column(name = "active_slot_id")
    private Long activeSlotId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public void markConfirmed(Long paymentId, Instant now) {
        this.status = BookingStatus.CONFIRMED;
        this.paymentId = paymentId;
        this.confirmedAt = now;
        this.holdExpiresAt = null;
    }

    public void markCompleted(Instant now) {
        this.status = BookingStatus.COMPLETED;
        this.completedAt = now;
    }

    /** Ends the booking as CANCELLED or EXPIRED and frees the unique "active slot" key. */
    public void closeAs(BookingStatus terminalStatus, String reason) {
        this.status = terminalStatus;
        this.cancelReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 200));
        this.activeSlotId = null;
        this.holdExpiresAt = null;
    }
}