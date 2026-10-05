package com.edumentor.review.service;

import com.edumentor.review.exception.ApiException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Sits outside the circuit breaker (Resilience4j's recommended order: Retry, CircuitBreaker, TimeLimiter).
 * When the circuit is open the Feign fallback answers instantly with a 503, so retries are cheap.
 */
@Slf4j
@Component
public class RemoteRetry {

    private final Retry retry;

    public RemoteRetry(@Value("${app.resilience.retry.max-attempts:3}") int maxAttempts,
                       @Value("${app.resilience.retry.wait:PT0.3S}") Duration wait) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(wait, 2.0))
                .retryOnException(ex -> ex instanceof ApiException api
                        && api.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .build();
        this.retry = Retry.of("remote-call", config);
        this.retry.getEventPublisher().onRetry(event ->
                log.warn("Retrying remote call: attempt={}, cause={}", event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null ? "unknown"
                                : event.getLastThrowable().getClass().getSimpleName()));
    }

    public <T> T call(Supplier<T> supplier) {
        return Retry.decorateSupplier(retry, supplier).get();
    }
}