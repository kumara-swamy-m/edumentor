package com.edumentor.ai.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException mentorNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MENTOR_NOT_FOUND",
                "Mentor was not found or is not approved");
    }

    public static ApiException mentorAccessDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "MENTOR_ACCESS_DENIED", "Mentor lookup was denied");
    }

    public static ApiException mentorServiceUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MENTOR_SERVICE_UNAVAILABLE",
                "Mentor data is temporarily unavailable, please try again shortly");
    }

    public static ApiException aiProviderUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE",
                "The AI provider is temporarily unavailable, please try again shortly");
    }

    public static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many recommendation requests, please wait a minute");
    }
}