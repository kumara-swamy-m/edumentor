package com.edumentor.ai.service;

import com.edumentor.ai.config.AiProperties;
import com.edumentor.ai.exception.ApiException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding one-minute window per user. In-memory, so it is per instance. Caps LLM cost and abuse. */
@Component
public class RecommendationRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private final Map<Long, Deque<Long>> calls = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limit;

    public RecommendationRateLimiter(Clock clock, AiProperties properties) {
        this.clock = clock;
        this.limit = properties.recommendationsPerMinute();
    }

    public void check(Long userId) {
        long now = clock.millis();
        Deque<Long> window = calls.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                throw ApiException.rateLimited();
            }
            window.addLast(now);
        }
    }
}