package com.edumentor.ai.service;

import com.edumentor.ai.exception.ApiException;
import com.edumentor.ai.provider.AiRecommendationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Single place where provider failures become a clean 503. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final AiRecommendationProvider provider;

    public float[] embed(String text) {
        try {
            return provider.embed(text);
        } catch (RuntimeException ex) {
            log.warn("Embedding failed: {}", ex.getClass().getSimpleName());
            throw ApiException.aiProviderUnavailable();
        }
    }
}