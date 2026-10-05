package com.edumentor.review.repository;

import com.edumentor.review.entity.Rating;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByMentorId(Long mentorId);

    /** SELECT ... FOR UPDATE: concurrent reviews for one mentor queue up behind this lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rating r where r.mentorId = :mentorId")
    Optional<Rating> findByMentorIdForUpdate(@Param("mentorId") Long mentorId);
}