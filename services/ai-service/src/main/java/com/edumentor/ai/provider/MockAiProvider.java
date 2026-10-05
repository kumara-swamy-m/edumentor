package com.edumentor.ai.provider;

import com.edumentor.ai.config.AiProperties;
import com.edumentor.ai.domain.Candidate;
import com.edumentor.ai.dto.RecommendationRequest;
import com.edumentor.ai.text.TextTokens;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiRecommendationProvider {

    private static final double BIGRAM_WEIGHT = 0.5;

    private final int dimensions;

    public MockAiProvider(AiProperties properties) {
        this.dimensions = properties.dimensions();
    }

    @Override
    public String modelId() {
        return "mock-hash-v1/" + dimensions;
    }

    @Override
    public float[] embed(String text) {
        List<String> tokens = TextTokens.tokens(text, false);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Text has no usable content");
        }
        double[] vector = new double[dimensions];
        for (int i = 0; i < tokens.size(); i++) {
            vector[bucket(tokens.get(i))] += 1.0;
            if (i > 0) {
                vector[bucket(tokens.get(i - 1) + " " + tokens.get(i))] += BIGRAM_WEIGHT;
            }
        }
        double norm = 0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        float[] result = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    @Override
    public Map<Long, String> explain(RecommendationRequest request, List<Candidate> candidates) {
        return Map.of(); // the service falls back to the fact-based template
    }

    /** FNV-1a: stable across JVMs and versions, unlike a hash derived from Object.hashCode. */
    private int bucket(String token) {
        int hash = 0x811c9dc5;
        for (byte b : token.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return Math.floorMod(hash, dimensions);
    }
}