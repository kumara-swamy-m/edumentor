package com.edumentor.ai.dto;

import java.util.List;

public record RecommendationResponse(String model, String scoreType, String scoreMeaning,
                                     List<RecommendationItem> recommendations) {

    public static final String SCORE_TYPE = "COSINE_SIMILARITY";
    public static final String SCORE_MEANING =
            "matchScore is the cosine similarity (0-1) between your request and the mentor profile. "
                    + "It is a relevance score, not a probability of any outcome.";
}