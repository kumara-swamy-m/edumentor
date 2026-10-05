package com.edumentor.review.repository;

import com.edumentor.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    Page<Review> findByMentorId(Long mentorId, Pageable pageable);

    Page<Review> findByStudentId(Long studentId, Pageable pageable);
}