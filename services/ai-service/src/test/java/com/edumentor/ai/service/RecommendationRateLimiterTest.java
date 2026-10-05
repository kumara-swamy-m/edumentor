package com.edumentor.ai.service;

import com.edumentor.ai.config.AiProperties;
import com.edumentor.ai.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-02T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final RecommendationRateLimiter limiter =
            new RecommendationRateLimiter(clock, new AiProperties("mock", 512, "memory", 2));

    @Test
    void callsBeyondTheLimitAreRejected() {
        limiter.check(1L);
        limiter.check(1L);

        assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "RATE_LIMITED");
    }

    @Test
    void usersAreCountedSeparately() {
        limiter.check(1L);
        limiter.check(1L);

        assertThatCode(() -> limiter.check(2L)).doesNotThrowAnyException();
    }

    @Test
    void theWindowSlidesAfterAMinute() {
        limiter.check(1L);
        limiter.check(1L);
        now.set(now.get().plusSeconds(61));

        assertThatCode(() -> limiter.check(1L)).doesNotThrowAnyException();
    }
}