package com.edumentor.booking.client;

import com.edumentor.booking.exception.ApiException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MentorClientFallbackFactory implements FallbackFactory<MentorClient> {

    private static final int MAX_CAUSE_DEPTH = 10;

    @Override
    public MentorClient create(Throwable cause) {
        ApiException translated = translate(cause);
        return new MentorClient() {
            @Override
            public MentorOwnProfile getOwnProfile(String authorization, String correlationId) {
                throw translated;
            }

            @Override
            public MentorPublicProfile getApprovedMentor(Long id, String authorization, String correlationId) {
                throw translated;
            }
        };
    }

    static ApiException translate(Throwable cause) {
        Throwable current = cause;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++, current = current.getCause()) {
            if (current instanceof FeignException.NotFound) {
                return ApiException.mentorNotFound();
            }
            if (current instanceof FeignException.Forbidden || current instanceof FeignException.Unauthorized) {
                return ApiException.mentorAccessDenied();
            }
        }
        log.warn("mentor-service call failed ({}); failing closed",
                cause == null ? "unknown" : cause.getClass().getSimpleName());
        return ApiException.mentorServiceUnavailable();
    }
}