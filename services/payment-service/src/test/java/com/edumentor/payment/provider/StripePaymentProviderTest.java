package com.edumentor.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripePaymentProviderTest {

    private static final String WEBHOOK_SECRET = "whsec_unit_test_secret";
    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private final StripePaymentProvider provider = new StripePaymentProvider(new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC), "sk_test_dummy", WEBHOOK_SECRET, "https://api.stripe.com");

    private static final String SUCCEEDED = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\","
            + "\"data\":{\"object\":{\"id\":\"pi_123\"}}}";
    private static final String FAILED = "{\"id\":\"evt_2\",\"type\":\"payment_intent.payment_failed\","
            + "\"data\":{\"object\":{\"id\":\"pi_123\",\"last_payment_error\":{\"message\":\"Your card was declined.\"}}}}";

    @Test
    void validSucceededEventIsParsed() {
        ProviderEvent event = provider.parseWebhook(SUCCEEDED, header(NOW.getEpochSecond(), SUCCEEDED, WEBHOOK_SECRET));

        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.outcome()).isEqualTo(ProviderOutcome.SUCCEEDED);
        assertThat(event.providerPaymentId()).isEqualTo("pi_123");
    }

    @Test
    void failedEventCarriesStripesMessage() {
        ProviderEvent event = provider.parseWebhook(FAILED, header(NOW.getEpochSecond(), FAILED, WEBHOOK_SECRET));

        assertThat(event.outcome()).isEqualTo(ProviderOutcome.FAILED);
        assertThat(event.reason()).isEqualTo("Your card was declined.");
    }

    @Test
    void unrelatedEventTypesAreIgnored() {
        String body = "{\"id\":\"evt_3\",\"type\":\"charge.refunded\",\"data\":{\"object\":{\"id\":\"ch_1\"}}}";

        assertThat(provider.parseWebhook(body, header(NOW.getEpochSecond(), body, WEBHOOK_SECRET)).outcome())
                .isEqualTo(ProviderOutcome.IGNORED);
    }

    @Test
    void wrongSecretIsRejected() {
        HttpHeaders headers = header(NOW.getEpochSecond(), SUCCEEDED, "whsec_someone_else");

        assertThatThrownBy(() -> provider.parseWebhook(SUCCEEDED, headers))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        HttpHeaders headers = header(NOW.getEpochSecond(), SUCCEEDED, WEBHOOK_SECRET);
        String tampered = SUCCEEDED.replace("pi_123", "pi_999");

        assertThatThrownBy(() -> provider.parseWebhook(tampered, headers))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void oldTimestampIsRejectedAsReplay() {
        HttpHeaders headers = header(NOW.getEpochSecond() - 301, SUCCEEDED, WEBHOOK_SECRET);

        assertThatThrownBy(() -> provider.parseWebhook(SUCCEEDED, headers))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void timestampJustInsideToleranceIsAccepted() {
        HttpHeaders headers = header(NOW.getEpochSecond() - 299, SUCCEEDED, WEBHOOK_SECRET);

        assertThat(provider.parseWebhook(SUCCEEDED, headers).outcome()).isEqualTo(ProviderOutcome.SUCCEEDED);
    }

    @Test
    void anyOneValidV1SignatureIsEnough() {
        long ts = NOW.getEpochSecond();
        String good = HmacSigner.hmacSha256Hex(WEBHOOK_SECRET, ts + "." + SUCCEEDED);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Stripe-Signature", "t=" + ts + ",v1=deadbeef,v1=" + good + ",v0=ignored");

        assertThat(provider.parseWebhook(SUCCEEDED, headers).outcome()).isEqualTo(ProviderOutcome.SUCCEEDED);
    }

    @Test
    void missingOrMalformedHeaderIsRejected() {
        assertThatThrownBy(() -> provider.parseWebhook(SUCCEEDED, new HttpHeaders()))
                .isInstanceOf(InvalidWebhookException.class);

        HttpHeaders noSignature = new HttpHeaders();
        noSignature.set("Stripe-Signature", "t=" + NOW.getEpochSecond());
        assertThatThrownBy(() -> provider.parseWebhook(SUCCEEDED, noSignature))
                .isInstanceOf(InvalidWebhookException.class);

        HttpHeaders badTimestamp = new HttpHeaders();
        badTimestamp.set("Stripe-Signature", "t=abc,v1=00");
        assertThatThrownBy(() -> provider.parseWebhook(SUCCEEDED, badTimestamp))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void missingKeysFailAtStartup() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThatThrownBy(() -> new StripePaymentProvider(new ObjectMapper(), clock, "", "whsec", "https://x"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new StripePaymentProvider(new ObjectMapper(), clock, "sk", "", "https://x"))
                .isInstanceOf(IllegalStateException.class);
    }

    private HttpHeaders header(long timestamp, String body, String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Stripe-Signature",
                "t=" + timestamp + ",v1=" + HmacSigner.hmacSha256Hex(secret, timestamp + "." + body));
        return headers;
    }
}