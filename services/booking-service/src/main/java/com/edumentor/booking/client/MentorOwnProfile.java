package com.edumentor.booking.client;

/** Subset of mentor-service's MentorResponse (GET /api/mentors/me). */
public record MentorOwnProfile(Long id, Long userId, String verificationStatus) {
}