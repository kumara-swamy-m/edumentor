package com.edumentor.payment.exception;

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

    // ----- payments -----

    public static ApiException paymentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found");
    }

    public static ApiException paymentAlreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_EXISTS",
                "A payment already exists for this booking");
    }

    public static ApiException idempotencyKeyReused() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different booking");
    }

    public static ApiException invalidIdempotencyKey() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must be 8-64 characters: letters, digits, '_' or '-'");
    }

    public static ApiException providerUnavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_UNAVAILABLE",
                "The payment provider is temporarily unavailable, please retry with the same Idempotency-Key");
    }

    // ----- webhooks -----

    public static ApiException invalidWebhookSignature() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_SIGNATURE",
                "Webhook signature verification failed");
    }

    public static ApiException webhookProviderUnknown() {
        return new ApiException(HttpStatus.NOT_FOUND, "WEBHOOK_PROVIDER_UNKNOWN",
                "No such payment provider is active");
    }

    // ----- booking-service outcomes -----

    public static ApiException bookingNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found");
    }

    public static ApiException bookingNotPayable() {
        return new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_PAYABLE",
                "The booking is not awaiting payment or its hold has expired");
    }

    public static ApiException bookingAccessDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "BOOKING_ACCESS_DENIED", "Booking lookup was denied");
    }

    public static ApiException bookingServiceUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_SERVICE_UNAVAILABLE",
                "Booking verification is temporarily unavailable, please try again shortly");
    }
}