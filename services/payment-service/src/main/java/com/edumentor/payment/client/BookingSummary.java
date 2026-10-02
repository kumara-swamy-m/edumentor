package com.edumentor.payment.client;

import java.math.BigDecimal;
import java.time.Instant;

/** Subset of booking-service's BookingResponse (GET /api/bookings/{id}). */
public record BookingSummary(Long id, Long studentId, String status, BigDecimal price, String currency,
                             Instant holdExpiresAt) {
}