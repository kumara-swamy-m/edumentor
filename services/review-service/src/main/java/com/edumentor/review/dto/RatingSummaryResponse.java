package com.edumentor.review.dto;

import java.math.BigDecimal;

public record RatingSummaryResponse(Long mentorId, BigDecimal averageRating, int reviewCount) {
}