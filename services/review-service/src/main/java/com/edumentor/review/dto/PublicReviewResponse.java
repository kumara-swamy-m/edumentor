package com.edumentor.review.dto;

import com.edumentor.review.entity.Review;

import java.time.Instant;

public record PublicReviewResponse(Long id, int rating, String comment, Instant createdAt) {

    public static PublicReviewResponse from(Review r) {
        return new PublicReviewResponse(r.getId(), r.getRating(), r.getComment(), r.getCreatedAt());
    }
}