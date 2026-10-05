package com.edumentor.ai.provider;

import com.edumentor.ai.domain.Candidate;
import com.edumentor.ai.dto.RecommendationRequest;

import java.util.List;
import java.util.Map;

public interface AiRecommendationProvider {

    /** Identifies the embedding model. Vectors from different models are never compared. */
    String modelId();

    /** Text to unit-length vector with the configured number of dimensions. */
    float[] embed(String text);

    /**
     * Optional natural-language reasons keyed by mentor id. Mentors left out get the template reason.
     * Implementations must not invent facts or mention scores.
     */
    Map<Long, String> explain(RecommendationRequest request, List<Candidate> candidates);
}