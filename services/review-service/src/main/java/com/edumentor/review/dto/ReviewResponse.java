package com.edumentor.review.dto;

import com.edumentor.review.entity.Review;

import java.time.Instant;

public record ReviewResponse(Long id, Long bookingId, Long mentorId, int rating, String comment,
                             Instant createdAt) {

    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getId(), r.getBookingId(), r.getMentorId(), r.getRating(), r.getComment(),
                r.getCreatedAt());
    }
}