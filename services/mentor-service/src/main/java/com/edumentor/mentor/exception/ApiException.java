package com.edumentor.mentor.exception;

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
        return new ApiException(HttpStatus.NOT_FOUND, "MENTOR_NOT_FOUND", "Mentor was not found");
    }

    public static ApiException profileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MENTOR_PROFILE_NOT_FOUND",
                "You have not created a mentor profile yet");
    }

    public static ApiException profileAlreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, "MENTOR_PROFILE_ALREADY_EXISTS",
                "A mentor profile already exists for this account");
    }

    public static ApiException mentorNotApproved() {
        return new ApiException(HttpStatus.FORBIDDEN, "MENTOR_NOT_APPROVED",
                "Only APPROVED mentors can perform this action");
    }

    public static ApiException invalidVerificationState(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_VERIFICATION_STATE", message);
    }

    public static ApiException verificationProofMissing() {
        return new ApiException(HttpStatus.CONFLICT, "VERIFICATION_PROOF_MISSING",
                "The mentor has not submitted any verification proof");
    }

    public static ApiException invalidAvailability(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVAILABILITY", message);
    }
}