package com.edumentor.mentor.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthClientFallbackFactory implements FallbackFactory<AuthClient> {

    @Override
    public AuthClient create(Throwable cause) {
        return (id, authorization, correlationId) -> {
            log.warn("auth-service lookup failed for userId={} ({}); continuing without applicant email",
                    id, cause.getClass().getSimpleName());
            return null;
        };
    }
}