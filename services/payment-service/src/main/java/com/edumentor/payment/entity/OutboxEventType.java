package com.edumentor.payment.entity;

import com.edumentor.payment.messaging.Topics;

public enum OutboxEventType {
    PAYMENT_SUCCESS(Topics.PAYMENT_EVENTS),
    PAYMENT_FAILED(Topics.PAYMENT_EVENTS),
    PAYMENT_REFUNDED(Topics.PAYMENT_EVENTS);

    private final String topic;

    OutboxEventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}