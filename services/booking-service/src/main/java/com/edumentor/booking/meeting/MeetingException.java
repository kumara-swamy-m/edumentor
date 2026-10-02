package com.edumentor.booking.meeting;

public class MeetingException extends RuntimeException {

    public MeetingException(String message) {
        super(message);
    }

    public MeetingException(String message, Throwable cause) {
        super(message, cause);
    }
}