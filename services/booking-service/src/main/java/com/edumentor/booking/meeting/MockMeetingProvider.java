package com.edumentor.booking.meeting;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development placeholder: a deterministic URL that is not a real meeting. */
@Component
@ConditionalOnProperty(name = "app.meeting.provider", havingValue = "mock", matchIfMissing = true)
public class MockMeetingProvider implements MeetingProvider {

    @Override
    public String createMeeting(MeetingRequest request) {
        return "https://meet.edumentor.local/session-" + request.bookingId();
    }
}