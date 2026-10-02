package com.edumentor.payment.client;

import com.edumentor.payment.exception.ApiException;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BookingClientFallbackFactoryTest {

    private final BookingClientFallbackFactory factory = new BookingClientFallbackFactory();

    @Test
    void notFoundBecomesBookingNotFound() {
        BookingClient fallback = factory.create(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> fallback.getBooking(1L, "Bearer x", "c"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_FOUND");
    }

    @Test
    void forbiddenBecomesAccessDenied() {
        BookingClient fallback = factory.create(new RuntimeException("wrapped", mock(FeignException.Forbidden.class)));

        assertThatThrownBy(() -> fallback.getBooking(1L, "Bearer x", "c"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_ACCESS_DENIED");
    }

    @Test
    void anyOtherFailureFailsClosedWith503() {
        BookingClient fallback = factory.create(new TimeoutException("slow"));

        assertThatThrownBy(() -> fallback.getBooking(1L, "Bearer x", "c"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_SERVICE_UNAVAILABLE")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(503));
    }
}