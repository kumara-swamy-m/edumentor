package com.edumentor.payment.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    public static final String SIGNATURE_HEADER = "X-Mock-Signature";
    private static final int MIN_SECRET_LENGTH = 16;

    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public MockPaymentProvider(ObjectMapper objectMapper,
                               @Value("${app.payment.mock.webhook-secret:}") String webhookSecret) {
        if (webhookSecret == null || webhookSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("MOCK_WEBHOOK_SECRET must be set to at least "
                    + MIN_SECRET_LENGTH + " characters when PAYMENT_PROVIDER=mock");
        }
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ProviderPayment createPayment(CreateProviderPayment request) {
        // Deterministic per payment, which also makes retries idempotent
        return new ProviderPayment("mock_pi_" + request.paymentId(), "mock_secret_" + request.paymentId());
    }

    /** Signs a webhook body exactly like the provider would (used by the dev simulator and tests). */
    public String sign(String rawBody) {
        return HmacSigner.hmacSha256Hex(webhookSecret, rawBody);
    }

    @Override
    public ProviderEvent parseWebhook(String rawBody, HttpHeaders headers) {
        String signature = headers.getFirst(SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            throw new InvalidWebhookException("Missing signature header");
        }
        if (!HmacSigner.constantTimeEquals(sign(rawBody), signature.trim().toLowerCase())) {
            throw new InvalidWebhookException("Signature mismatch");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventId = root.path("id").asText(null);
            String type = root.path("type").asText(null);
            if (eventId == null || eventId.isBlank() || type == null) {
                throw new InvalidWebhookException("Malformed event");
            }
            ProviderOutcome outcome = switch (type) {
                case "payment.succeeded" -> ProviderOutcome.SUCCEEDED;
                case "payment.failed" -> ProviderOutcome.FAILED;
                default -> ProviderOutcome.IGNORED;
            };
            String providerPaymentId = root.path("providerPaymentId").asText(null);
            if (outcome != ProviderOutcome.IGNORED && (providerPaymentId == null || providerPaymentId.isBlank())) {
                throw new InvalidWebhookException("Malformed event");
            }
            return new ProviderEvent(eventId, outcome, providerPaymentId, root.path("reason").asText(null));
        } catch (JsonProcessingException ex) {
            throw new InvalidWebhookException("Malformed event");
        }
    }

    @Override
    public ProviderOutcome fetchStatus(String providerPaymentId) {
        // The mock has no remote state; completion only ever arrives through a signed webhook.
        return ProviderOutcome.PENDING;
    }
}