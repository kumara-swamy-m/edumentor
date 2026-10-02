package com.edumentor.booking.client;

/** Subset of mentor-service's PublicMentorResponse (GET /api/mentors/{id}, approved mentors only). */
public record MentorPublicProfile(Long id) {
}