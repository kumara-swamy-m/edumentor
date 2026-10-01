package com.edumentor.auth.exception;

public class RoleNotAllowedException extends RuntimeException {

    public RoleNotAllowedException(String message) {
        super(message);
    }
}