package com.edumentor.gateway.web;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public record ErrorResponse(String timestamp, int status, String error, String message, String path) {

    public static ErrorResponse of(HttpStatus status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now().toString(), status.value(), error, message, path);
    }
}