package com.edumentor.booking.event;

import java.time.Instant;

public record SessionReminderEvent(
        String eventId, String eventType, Instant occurredAt, String correlationId,
        Long bookingId, Long studentId, Long mentorUserId, Instant sessionStart, Instant sessionEnd,
        String meetingUrl) {
}