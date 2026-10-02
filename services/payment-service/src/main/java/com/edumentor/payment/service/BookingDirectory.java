package com.edumentor.payment.service;

import com.edumentor.payment.client.BookingClient;
import com.edumentor.payment.client.BookingSummary;
import com.edumentor.payment.exception.ApiException;
import com.edumentor.payment.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Domain wrapper around the booking-service client: the price and payability come from there. */
@Component
@RequiredArgsConstructor
public class BookingDirectory {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";

    private final BookingClient bookingClient;
    private final Clock clock;

    public BookingSummary requirePayable(Long bookingId, String authorization, Long studentId) {
        BookingSummary booking = bookingClient.getBooking(bookingId, authorization,
                MDC.get(CorrelationIdFilter.MDC_KEY));
        if (booking == null || booking.price() == null || booking.currency() == null) {
            throw ApiException.bookingServiceUnavailable();
        }
        if (!studentId.equals(booking.studentId())) {
            throw ApiException.bookingNotFound();
        }
        boolean holdExpired = booking.holdExpiresAt() != null && !booking.holdExpiresAt().isAfter(Instant.now(clock));
        if (!PENDING_PAYMENT.equals(booking.status()) || holdExpired) {
            throw ApiException.bookingNotPayable();
        }
        return booking;
    }
}