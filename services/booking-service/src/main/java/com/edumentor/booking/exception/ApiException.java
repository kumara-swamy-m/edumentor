package com.edumentor.booking.exception;

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

    // ----- slots -----

    public static ApiException slotNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Slot was not found");
    }

    public static ApiException slotNotAvailable() {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", "This slot is no longer available");
    }

    public static ApiException slotTooSoon() {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_TOO_SOON",
                "This slot starts too soon to be booked");
    }

    public static ApiException slotOverlap() {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_OVERLAP",
                "The slot overlaps with one of your existing slots");
    }

    public static ApiException slotNotCancellable() {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_NOT_CANCELLABLE",
                "Only AVAILABLE slots can be cancelled");
    }

    public static ApiException invalidSlotTime(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT_TIME", message);
    }

    // ----- bookings -----

    public static ApiException bookingNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found");
    }

    public static ApiException bookingNotCancellable() {
        return new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_CANCELLABLE",
                "Only bookings awaiting payment can be cancelled online");
    }

    public static ApiException bookingNotPending() {
        return new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_PENDING",
                "The booking is not awaiting payment");
    }

    public static ApiException sessionNotStarted() {
        return new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_STARTED",
                "The session has not started yet");
    }

    public static ApiException invalidBookingState(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_BOOKING_STATE", message);
    }

    public static ApiException tooManyPendingBookings() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_PENDING_BOOKINGS",
                "You have too many unpaid holds; pay for or cancel one first");
    }

    // ----- mentor-service outcomes -----

    public static ApiException mentorNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MENTOR_NOT_FOUND",
                "Mentor was not found or is not approved");
    }

    public static ApiException mentorNotApproved() {
        return new ApiException(HttpStatus.FORBIDDEN, "MENTOR_NOT_APPROVED",
                "Only APPROVED mentors can perform this action");
    }

    public static ApiException mentorAccessDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "MENTOR_ACCESS_DENIED",
                "Mentor lookup was denied");
    }

    public static ApiException mentorServiceUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MENTOR_SERVICE_UNAVAILABLE",
                "Mentor verification is temporarily unavailable, please try again shortly");
    }
}