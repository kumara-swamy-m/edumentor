package com.edumentor.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentProviderTest {

    private static final String SECRET = "unit-test-mock-secret-123456";

    private final MockPaymentProvider provider = new MockPaymentProvider(new ObjectMapper(), SECRET);

    @Test
    void validSucceededEventIsParsed() {
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"providerPaymentId\":\"mock_pi_7\"}";

        ProviderEvent event = provider.parseWebhook(body, signed(body, SECRET));

        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.outcome()).isEqualTo(ProviderOutcome.SUCCEEDED);
        assertThat(event.providerPaymentId()).isEqualTo("mock_pi_7");
    }

    @Test
    void validFailedEventCarriesTheReason() {
        String body = "{\"id\":\"evt_2\",\"type\":\"payment.failed\",\"providerPaymentId\":\"mock_pi_7\","
                + "\"reason\":\"Card declined\"}";

        ProviderEvent event = provider.parseWebhook(body, signed(body, SECRET));

        assertThat(event.outcome()).isEqualTo(ProviderOutcome.FAILED);
        assertThat(event.reason()).isEqualTo("Card declined");
    }

    @Test
    void unknownEventTypeIsIgnored() {
        String body = "{\"id\":\"evt_3\",\"type\":\"customer.created\"}";

        assertThat(provider.parseWebhook(body, signed(body, SECRET)).outcome()).isEqualTo(ProviderOutcome.IGNORED);
    }

    @Test
    void tamperedBodyIsRejected() {
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"providerPaymentId\":\"mock_pi_7\"}";
        HttpHeaders headers = signed(body, SECRET);
        String tampered = body.replace("mock_pi_7", "mock_pi_8");

        assertThatThrownBy(() -> provider.parseWebhook(tampered, headers))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void signatureFromAnotherSecretIsRejected() {
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"providerPaymentId\":\"mock_pi_7\"}";

        assertThatThrownBy(() -> provider.parseWebhook(body, signed(body, "another-secret-value-123456")))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void missingSignatureHeaderIsRejected() {
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"providerPaymentId\":\"mock_pi_7\"}";

        assertThatThrownBy(() -> provider.parseWebhook(body, new HttpHeaders()))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void validlySignedButMalformedJsonIsRejected() {
        String body = "not json at all";

        assertThatThrownBy(() -> provider.parseWebhook(body, signed(body, SECRET)))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void missingSecretOrShortSecretFailsAtStartup() {
        assertThatThrownBy(() -> new MockPaymentProvider(new ObjectMapper(), ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MockPaymentProvider(new ObjectMapper(), "short"))
                .isInstanceOf(IllegalStateException.class);
    }

    private HttpHeaders signed(String body, String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(MockPaymentProvider.SIGNATURE_HEADER, HmacSigner.hmacSha256Hex(secret, body));
        return headers;
    }
}