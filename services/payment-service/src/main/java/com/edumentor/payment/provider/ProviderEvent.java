package com.edumentor.payment.provider;

/** A verified webhook event, already translated to our own vocabulary. */
public record ProviderEvent(String eventId, ProviderOutcome outcome, String providerPaymentId, String reason) {
}