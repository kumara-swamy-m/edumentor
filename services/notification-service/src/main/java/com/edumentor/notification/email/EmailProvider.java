package com.edumentor.notification.email;

public interface EmailProvider {

    /** Sends the email or throws a RuntimeException. */
    void send(EmailMessage message);
}