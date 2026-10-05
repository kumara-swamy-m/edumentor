package com.edumentor.ai.dto;

public record RecommendationItem(
        Long mentorId,
        String name,
        String college,
        String branch,
        String examPath,
        double rating,
        double matchScore,
        String reason
) {
}