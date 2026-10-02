package com.edumentor.notification.dto;

import com.edumentor.notification.entity.Notification;
import com.edumentor.notification.entity.NotificationStatus;
import com.edumentor.notification.entity.NotificationType;

import java.time.Instant;

public record NotificationResponse(Long id, NotificationType type, NotificationStatus status, String subject,
                                   String body, Instant createdAt, Instant sentAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getStatus(), n.getSubject(), n.getBody(),
                n.getCreatedAt(), n.getSentAt());
    }
}