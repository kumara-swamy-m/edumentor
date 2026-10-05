package com.edumentor.review.event;

import java.math.BigDecimal;
import java.time.Instant;

public record MentorRatingUpdatedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        Long mentorId,
        Long reviewId,
        Long bookingId,
        BigDecimal averageRating,
        int reviewCount
) {
}