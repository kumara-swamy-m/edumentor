package com.edumentor.payment.provider;

/** The webhook failed signature verification or is malformed. Messages must never contain secrets. */
public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException(String message) {
        super(message);
    }
}