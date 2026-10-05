package com.edumentor.review.entity;

import com.edumentor.review.messaging.Topics;

public enum OutboxEventType {
    MENTOR_RATING_UPDATED(Topics.REVIEW_EVENTS);

    private final String topic;

    OutboxEventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}