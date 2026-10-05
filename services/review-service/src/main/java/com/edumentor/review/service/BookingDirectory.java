package com.edumentor.review.service;

import com.edumentor.review.client.BookingClient;
import com.edumentor.review.client.BookingInfo;
import com.edumentor.review.exception.ApiException;
import com.edumentor.review.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingDirectory {

    private static final String COMPLETED = "COMPLETED";

    private final BookingClient bookingClient;
    private final RemoteRetry remoteRetry;

    /** The booking must be the caller's own and COMPLETED. Someone else's booking looks like it does not exist. */
    public BookingInfo requireCompletedOwn(Long bookingId, String authorization, Long studentId) {
        BookingInfo booking = remoteRetry.call(() -> bookingClient.getBooking(bookingId, authorization,
                MDC.get(CorrelationIdFilter.MDC_KEY)));
        if (booking == null || booking.mentorId() == null) {
            throw ApiException.bookingServiceUnavailable();
        }
        if (!studentId.equals(booking.studentId())) {
            throw ApiException.bookingNotFound();
        }
        if (!COMPLETED.equals(booking.status())) {
            throw ApiException.bookingNotCompleted();
        }
        return booking;
    }
}