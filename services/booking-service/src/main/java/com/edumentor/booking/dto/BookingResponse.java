package com.edumentor.booking.dto;

import com.edumentor.booking.entity.Booking;
import com.edumentor.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long slotId,
        Long studentId,
        Long mentorId,
        BookingStatus status,
        BigDecimal price,
        String currency,
        Instant sessionStart,
        Instant sessionEnd,
        Instant holdExpiresAt,
        Long paymentId,
        String meetingUrl,
        String cancelReason,
        Instant createdAt,
        Instant confirmedAt
) {

    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getSlotId(), b.getStudentId(), b.getMentorId(), b.getStatus(),
                b.getPrice(), b.getCurrency(), b.getSessionStart(), b.getSessionEnd(), b.getHoldExpiresAt(),
                b.getPaymentId(), b.getMeetingUrl(), b.getCancelReason(), b.getCreatedAt(), b.getConfirmedAt());
    }
}