package com.edumentor.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ApiGatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedRouteWithoutTokenReturns401WithCorrelationId() {
        webTestClient.get().uri("/api/mentors/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-ID")
                .expectBody().jsonPath("$.error").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void incomingCorrelationIdIsEchoedBack() {
        webTestClient.get().uri("/api/bookings/1")
                .header("X-Correlation-ID", "trace-abc-123")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-ID", "trace-abc-123");
    }

    @Test
    void invalidTokenReturns401() {
        webTestClient.get().uri("/api/reviews/mentor/1")
                .header("Authorization", "Bearer not.a.jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.error").isEqualTo("INVALID_TOKEN");
    }



    @Test
    void healthEndpointIsUp() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}