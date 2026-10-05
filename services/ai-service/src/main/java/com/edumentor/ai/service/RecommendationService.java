package com.edumentor.ai.service;

import com.edumentor.ai.domain.Candidate;
import com.edumentor.ai.domain.ScoredMentor;
import com.edumentor.ai.dto.RecommendationItem;
import com.edumentor.ai.dto.RecommendationRequest;
import com.edumentor.ai.dto.RecommendationResponse;
import com.edumentor.ai.provider.AiRecommendationProvider;
import com.edumentor.ai.store.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 5;

    private final AiRecommendationProvider provider;
    private final EmbeddingService embeddingService;
    private final VectorStore store;
    private final MatchAnalyzer analyzer;
    private final RecommendationRateLimiter rateLimiter;

    public RecommendationResponse recommend(Long userId, RecommendationRequest request) {
        rateLimiter.check(userId);
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();

        float[] query = embeddingService.embed(DocumentBuilder.queryText(request));
        List<ScoredMentor> found = store.search(query, provider.modelId(), request.exam().name(), limit);

        List<Candidate> candidates = found.stream()
                .map(s -> new Candidate(s, analyzer.analyze(request, s.document().profile())))
                .toList();
        Map<Long, String> reasons = explain(request, candidates);

        List<RecommendationItem> items = candidates.stream()
                .map(c -> new RecommendationItem(
                        c.mentorId(),
                        c.scored().document().profile().name(),
                        c.scored().document().profile().college(),
                        c.scored().document().profile().branch(),
                        c.scored().document().examPath(),
                        c.scored().document().profile().rating(),
                        Math.round(c.scored().score() * 100.0) / 100.0,
                        reasons.getOrDefault(c.mentorId(), analyzer.templateReason(c))))
                .toList();

        log.info("Recommendations produced: userId={}, exam={}, results={}", userId, request.exam(), items.size());
        return new RecommendationResponse(provider.modelId(), RecommendationResponse.SCORE_TYPE,
                RecommendationResponse.SCORE_MEANING, items);
    }

    /** An explanation problem must never fail the recommendation: templates take over. */
    private Map<Long, String> explain(RecommendationRequest request, List<Candidate> candidates) {
        try {
            return provider.explain(request, candidates);
        } catch (RuntimeException ex) {
            log.warn("Explanation failed, using templates: {}", ex.getClass().getSimpleName());
            return Map.of();
        }
    }
}