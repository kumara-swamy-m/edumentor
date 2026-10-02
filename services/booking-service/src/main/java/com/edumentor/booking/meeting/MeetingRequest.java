package com.edumentor.booking.meeting;

import java.time.Instant;

public record MeetingRequest(Long bookingId, String title, Instant start, Instant end) {
}