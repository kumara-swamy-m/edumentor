package com.edumentor.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "mentor_ratings",
        uniqueConstraints = @UniqueConstraint(name = "uk_rating_mentor", columnNames = "mentor_id"))
@Getter
@NoArgsConstructor
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public static Rating forMentor(Long mentorId) {
        Rating rating = new Rating();
        rating.mentorId = mentorId;
        return rating;
    }

    public void add(int stars) {
        this.ratingSum += stars;
        this.ratingCount += 1;
    }

    public BigDecimal average() {
        if (ratingCount == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
    }
}