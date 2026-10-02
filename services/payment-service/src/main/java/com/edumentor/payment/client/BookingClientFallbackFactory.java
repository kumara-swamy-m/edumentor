package com.edumentor.payment.client;

import com.edumentor.payment.exception.ApiException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BookingClientFallbackFactory implements FallbackFactory<BookingClient> {

    private static final int MAX_CAUSE_DEPTH = 10;

    @Override
    public BookingClient create(Throwable cause) {
        ApiException translated = translate(cause);
        return (id, authorization, correlationId) -> {
            throw translated;
        };
    }

    static ApiException translate(Throwable cause) {
        Throwable current = cause;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++, current = current.getCause()) {
            if (current instanceof FeignException.NotFound) {
                return ApiException.bookingNotFound();
            }
            if (current instanceof FeignException.Forbidden || current instanceof FeignException.Unauthorized) {
                return ApiException.bookingAccessDenied();
            }
        }
        log.warn("booking-service call failed ({}); failing closed",
                cause == null ? "unknown" : cause.getClass().getSimpleName());
        return ApiException.bookingServiceUnavailable();
    }
}