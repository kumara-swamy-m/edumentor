package com.edumentor.payment.security;

/** Identity taken from a verified JWT. */
public record AuthenticatedUser(Long userId, String email, String role) {
}