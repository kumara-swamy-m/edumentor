package com.edumentor.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "gateway-test-secret-key-at-least-32-bytes!!";
    private static final String OTHER_SECRET = "another-gateway-secret-key-at-least-32-bytes";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new ObjectMapper(), SECRET);
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    @BeforeEach
    void setUp() {
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void publicLoginPathNeedsNoToken() {
        MockServerWebExchange exchange = exchange("/api/auth/login", null);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void paymentWebhookPathIsPublic() {
        MockServerWebExchange exchange = exchange("/api/payments/webhooks/stripe", null);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void missingTokenIsRejectedWith401() {
        MockServerWebExchange exchange = exchange("/api/mentors/1", null);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("UNAUTHORIZED");
        verify(chain, never()).filter(any());
    }

    @Test
    void garbageTokenIsRejectedWith401() {
        MockServerWebExchange exchange = exchange("/api/mentors/1", "not.a.jwt");

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("INVALID_TOKEN");
        verify(chain, never()).filter(any());
    }

    @Test
    void expiredTokenIsRejectedWith401() {
        String token = token(SECRET, -1_000, 1L, "a@b.com", "STUDENT");
        MockServerWebExchange exchange = exchange("/api/mentors/1", token);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejectedWith401() {
        String token = token(OTHER_SECRET, 60_000, 1L, "a@b.com", "ADMIN");
        MockServerWebExchange exchange = exchange("/api/auth/admin/users", token);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void tokenWithoutIdentityClaimsIsRejectedWith401() {
        String token = Jwts.builder().subject("a@b.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockServerWebExchange exchange = exchange("/api/mentors/1", token);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void validTokenForwardsIdentityFromVerifiedClaims() {
        String token = token(SECRET, 60_000, 42L, "asha@example.com", "STUDENT");
        MockServerWebExchange exchange = exchange("/api/mentors/1", token);

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = forwardedHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(forwarded.getFirst("X-User-Email")).isEqualTo("asha@example.com");
        assertThat(forwarded.getFirst("X-User-Role")).isEqualTo("STUDENT");
        assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    @Test
    void spoofedIdentityHeadersAreOverwrittenOnProtectedPath() {
        String token = token(SECRET, 60_000, 42L, "asha@example.com", "STUDENT");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/mentors/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "1")
                .build());

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = forwardedHeaders();
        assertThat(forwarded.get("X-User-Role")).containsExactly("STUDENT");
        assertThat(forwarded.get("X-User-Id")).containsExactly("42");
    }

    @Test
    void spoofedIdentityHeadersAreStrippedOnPublicPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "1")
                .build());

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = forwardedHeaders();
        assertThat(forwarded.containsKey("X-User-Role")).isFalse();
        assertThat(forwarded.containsKey("X-User-Id")).isFalse();
    }

    @Test
    void studentIsForbiddenFromAdminPaths() {
        String token = token(SECRET, 60_000, 2L, "s@b.com", "STUDENT");

        MockServerWebExchange authAdmin = exchange("/api/auth/admin/users", token);
        filter.filter(authAdmin, chain).block();
        MockServerWebExchange mentorAdmin = exchange("/api/mentors/admin/pending", token);
        filter.filter(mentorAdmin, chain).block();

        assertThat(authAdmin.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mentorAdmin.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void mentorIsForbiddenFromAdminPaths() {
        String token = token(SECRET, 60_000, 3L, "m@b.com", "MENTOR");
        MockServerWebExchange exchange = exchange("/api/mentors/admin/pending", token);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void adminIsAllowedOnAdminPaths() {
        String token = token(SECRET, 60_000, 1L, "admin@b.com", "ADMIN");
        MockServerWebExchange exchange = exchange("/api/mentors/admin/pending", token);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(forwardedHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void corsPreflightPassesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/mentors/1").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter(new ObjectMapper(), "too-short"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- helpers ----------

    private HttpHeaders forwardedHeaders() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest().getHeaders();
    }

    private MockServerWebExchange exchange(String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(request.build());
    }

    private String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    private String token(String secret, long ttlMs, Long userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}