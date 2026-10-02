package com.edumentor.booking.entity;

import com.edumentor.booking.messaging.Topics;

public enum OutboxEventType {
    BOOKING_CREATED(Topics.BOOKING_EVENTS),
    BOOKING_CONFIRMED(Topics.BOOKING_EVENTS),
    BOOKING_CANCELLED(Topics.BOOKING_EVENTS),
    BOOKING_PAYMENT_REJECTED(Topics.BOOKING_EVENTS),
    SESSION_REMINDER(Topics.NOTIFICATION_EVENTS);

    private final String topic;

    OutboxEventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}