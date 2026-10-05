package com.edumentor.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_booking", columnNames = "booking_id"),
        indexes = {
                @Index(name = "idx_review_mentor", columnList = "mentor_id,created_at"),
                @Index(name = "idx_review_student", columnList = "student_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** booking-service booking id (no cross-database foreign key). */
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** mentor-service profile id. */
    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    /** auth-service user id of the reviewing student. */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private int rating;

    @Column(name = "review_text", length = 1000)
    private String comment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}