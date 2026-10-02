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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "mentor_slots", indexes = {
        @Index(name = "idx_slot_mentor_status_start", columnList = "mentor_id,status,start_time"),
        @Index(name = "idx_slot_mentor_user", columnList = "mentor_user_id"),
        @Index(name = "idx_slot_status_start", columnList = "status,start_time")
})
@Getter
@Setter
@NoArgsConstructor
public class MentorSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** mentor-service profile id (no cross-database foreign key). */
    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    /** auth-service user id of the mentor. */
    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotStatus status = SlotStatus.AVAILABLE;

    /** Student who currently holds or has booked the slot. */
    @Column(name = "reserved_by_student_id")
    private Long reservedByStudentId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public void hold(Long studentId, Instant expiresAt) {
        this.status = SlotStatus.HELD;
        this.reservedByStudentId = studentId;
        this.holdExpiresAt = expiresAt;
    }

    public void release() {
        this.status = SlotStatus.AVAILABLE;
        this.reservedByStudentId = null;
        this.holdExpiresAt = null;
    }

    public void markBooked() {
        this.status = SlotStatus.BOOKED;
        this.holdExpiresAt = null;
    }
}