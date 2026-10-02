package com.edumentor.payment.provider;

import org.springframework.http.HttpHeaders;

public interface PaymentProvider {

    /** Short lowercase name, also used in the webhook URL: /api/payments/webhooks/{name}. */
    String name();

    /** Registers the payment with the provider. Must be idempotent for the same idempotencyKey. */
    ProviderPayment createPayment(CreateProviderPayment request);

    /**
     * Verifies the webhook signature over the exact raw body and translates the event.
     *
     * @throws InvalidWebhookException when the signature is wrong or the payload is malformed
     */
    ProviderEvent parseWebhook(String rawBody, HttpHeaders headers);

    /** Asks the provider for the current state (never trusts the client). */
    ProviderOutcome fetchStatus(String providerPaymentId);

    /** Refunds the full payment. Must be idempotent for the same idempotencyKey. */
    void refund(String providerPaymentId, String idempotencyKey);
}