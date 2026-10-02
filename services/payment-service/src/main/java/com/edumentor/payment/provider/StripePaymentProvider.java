package com.edumentor.payment.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe")
public class StripePaymentProvider implements PaymentProvider {

    public static final String SIGNATURE_HEADER = "Stripe-Signature";
    private static final long TOLERANCE_SECONDS = 300;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String webhookSecret;
    private final RestClient restClient;

    public StripePaymentProvider(ObjectMapper objectMapper,
                                 Clock clock,
                                 @Value("${app.payment.stripe.secret-key:}") String secretKey,
                                 @Value("${app.payment.stripe.webhook-secret:}") String webhookSecret,
                                 @Value("${app.payment.stripe.base-url:https://api.stripe.com}") String baseUrl) {
        if (secretKey == null || secretKey.isBlank() || webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException(
                    "STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET must be set when PAYMENT_PROVIDER=stripe");
        }
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webhookSecret = webhookSecret;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .build();
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public ProviderPayment createPayment(CreateProviderPayment request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(toMinorUnits(request.amount())));
        form.add("currency", request.currency().toLowerCase());
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("metadata[payment_id]", String.valueOf(request.paymentId()));
        form.add("metadata[booking_id]", String.valueOf(request.bookingId()));
        form.add("metadata[student_id]", String.valueOf(request.studentId()));
        try {
            JsonNode node = restClient.post()
                    .uri("/v1/payment_intents")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String id = node == null ? null : node.path("id").asText(null);
            String clientSecret = node == null ? null : node.path("client_secret").asText(null);
            if (id == null || clientSecret == null) {
                throw new ProviderException("Stripe response is missing id or client_secret");
            }
            return new ProviderPayment(id, clientSecret);
        } catch (RestClientException ex) {
            throw new ProviderException("Stripe payment creation failed", ex);
        }
    }

    @Override
    public ProviderEvent parseWebhook(String rawBody, HttpHeaders headers) {
        String header = headers.getFirst(SIGNATURE_HEADER);
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookException("Missing signature header");
        }

        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if ("t".equals(keyValue[0])) {
                timestamp = keyValue[1];
            } else if ("v1".equals(keyValue[0])) {
                signatures.add(keyValue[1].toLowerCase());
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidWebhookException("Malformed signature header");
        }

        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new InvalidWebhookException("Malformed signature header");
        }
        if (Math.abs(clock.instant().getEpochSecond() - signedAt) > TOLERANCE_SECONDS) {
            throw new InvalidWebhookException("Timestamp outside tolerance");
        }

        String expected = HmacSigner.hmacSha256Hex(webhookSecret, timestamp + "." + rawBody);
        boolean valid = signatures.stream().anyMatch(candidate -> HmacSigner.constantTimeEquals(expected, candidate));
        if (!valid) {
            throw new InvalidWebhookException("Signature mismatch");
        }
        return toEvent(rawBody);
    }

    @Override
    public ProviderOutcome fetchStatus(String providerPaymentId) {
        try {
            JsonNode node = restClient.get()
                    .uri("/v1/payment_intents/{id}", providerPaymentId)
                    .retrieve()
                    .body(JsonNode.class);
            String status = node == null ? "" : node.path("status").asText("");
            return switch (status) {
                case "succeeded" -> ProviderOutcome.SUCCEEDED;
                case "canceled" -> ProviderOutcome.FAILED;
                default -> ProviderOutcome.PENDING;
            };
        } catch (RestClientException ex) {
            throw new ProviderException("Stripe status lookup failed", ex);
        }
    }
    @Override
    public void refund(String providerPaymentId, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", providerPaymentId);
        try {
            restClient.post()
                    .uri("/v1/refunds")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ProviderException("Stripe refund failed", ex);
        }
    }

    private ProviderEvent toEvent(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventId = root.path("id").asText(null);
            String type = root.path("type").asText(null);
            if (eventId == null || eventId.isBlank() || type == null) {
                throw new InvalidWebhookException("Malformed event");
            }
            ProviderOutcome outcome = switch (type) {
                case "payment_intent.succeeded" -> ProviderOutcome.SUCCEEDED;
                case "payment_intent.payment_failed" -> ProviderOutcome.FAILED;
                default -> ProviderOutcome.IGNORED;
            };
            JsonNode object = root.path("data").path("object");
            String providerPaymentId = object.path("id").asText(null);
            if (outcome != ProviderOutcome.IGNORED && (providerPaymentId == null || providerPaymentId.isBlank())) {
                throw new InvalidWebhookException("Malformed event");
            }
            String reason = object.path("last_payment_error").path("message").asText(null);
            return new ProviderEvent(eventId, outcome, providerPaymentId, reason);
        } catch (JsonProcessingException ex) {
            throw new InvalidWebhookException("Malformed event");
        }
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}