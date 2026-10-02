package com.edumentor.booking.client;

import com.edumentor.booking.exception.ApiException;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MentorClientFallbackFactoryTest {

    private final MentorClientFallbackFactory factory = new MentorClientFallbackFactory();

    @Test
    void notFoundFromMentorServiceBecomesMentorNotFound() {
        MentorClient fallback = factory.create(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> fallback.getApprovedMentor(1L, "Bearer x", "c"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MENTOR_NOT_FOUND");
    }

    @Test
    void wrappedNotFoundIsStillRecognised() {
        MentorClient fallback = factory.create(new RuntimeException("wrapped", mock(FeignException.NotFound.class)));

        assertThatThrownBy(() -> fallback.getOwnProfile("Bearer x", "c"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MENTOR_NOT_FOUND");
    }

    @Test
    void forbiddenBecomesAccessDenied() {
        MentorClient fallback = factory.create(mock(FeignException.Forbidden.class));

        assertThatThrownBy(() -> fallback.getOwnProfile("Bearer x", "c"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MENTOR_ACCESS_DENIED");
    }

    @Test
    void anyOtherFailureFailsClosedWith503() {
        MentorClient fallback = factory.create(new TimeoutException("slow"));

        assertThatThrownBy(() -> fallback.getApprovedMentor(1L, "Bearer x", "c"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MENTOR_SERVICE_UNAVAILABLE")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(503));
    }
}