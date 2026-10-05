package com.edumentor.review.exception;

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

    public static ApiException reviewAlreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS",
                "This booking has already been reviewed");
    }

    public static ApiException bookingNotCompleted() {
        return new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_COMPLETED",
                "Only completed sessions can be reviewed");
    }

    public static ApiException bookingNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found");
    }

    public static ApiException bookingAccessDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "BOOKING_ACCESS_DENIED", "Booking lookup was denied");
    }

    public static ApiException bookingServiceUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_SERVICE_UNAVAILABLE",
                "Booking verification is temporarily unavailable, please try again shortly");
    }
}