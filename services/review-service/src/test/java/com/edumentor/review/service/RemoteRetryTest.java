package com.edumentor.review.service;

import com.edumentor.review.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteRetryTest {

    private final RemoteRetry retry = new RemoteRetry(3, Duration.ofMillis(5));

    @Test
    void retriesServiceUnavailableUntilTheCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = retry.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw ApiException.bookingServiceUnavailable();
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.call(() -> {
            calls.incrementAndGet();
            throw ApiException.bookingServiceUnavailable();
        })).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_SERVICE_UNAVAILABLE");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void businessAnswersAreNeverRetried() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.call(() -> {
            calls.incrementAndGet();
            throw ApiException.bookingNotFound();
        })).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "BOOKING_NOT_FOUND");
        assertThat(calls.get()).isEqualTo(1);
    }
}