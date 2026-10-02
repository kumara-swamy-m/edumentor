package com.edumentor.booking.security;

/** Identity taken from a verified JWT. */
public record AuthenticatedUser(Long userId, String email, String role) {
}