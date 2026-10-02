package com.edumentor.payment.provider;

public enum ProviderOutcome {
    SUCCEEDED,
    FAILED,
    /** Provider says the payment is still in progress (used by verify). */
    PENDING,
    /** A webhook event type we do not act on. */
    IGNORED
}