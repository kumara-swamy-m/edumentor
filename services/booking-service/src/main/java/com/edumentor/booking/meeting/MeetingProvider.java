package com.edumentor.booking.meeting;

public interface MeetingProvider {

    /** Creates the online meeting and returns its join URL. Must be idempotent per booking id. */
    String createMeeting(MeetingRequest request);
}