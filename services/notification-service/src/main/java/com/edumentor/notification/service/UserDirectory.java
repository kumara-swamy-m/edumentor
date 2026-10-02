package com.edumentor.notification.service;

import com.edumentor.notification.client.AuthClient;
import com.edumentor.notification.client.UserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserDirectory {

    private final AuthClient authClient;
    private final String apiKey;

    public UserDirectory(AuthClient authClient, @Value("${app.internal.api-key:}") String apiKey) {
        this.authClient = authClient;
        this.apiKey = apiKey;
    }

    /** Failures propagate so the Kafka consumer retries and, eventually, dead-letters the event. */
    public UserInfo require(Long userId) {
        UserInfo user = authClient.getUser(userId, apiKey);
        if (user == null || user.email() == null || user.email().isBlank()) {
            throw new IllegalStateException("No email found for userId=" + userId);
        }
        return user;
    }
}