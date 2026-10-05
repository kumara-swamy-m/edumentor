package com.edumentor.review.client;

/** Subset of booking-service's BookingResponse (GET /api/bookings/{id}). */
public record BookingInfo(Long id, Long studentId, Long mentorId, String status) {
}