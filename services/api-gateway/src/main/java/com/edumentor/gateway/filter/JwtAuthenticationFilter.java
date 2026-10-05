package com.edumentor.gateway.filter;

import com.edumentor.gateway.web.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;

    /** No JWT required. Webhooks are authenticated by provider signature in payment-service. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/payments/webhooks/**");

    /** Path pattern -> required role (defense in depth; services enforce roles too). */
    private static final Map<String, String> ROLE_RULES = new LinkedHashMap<>();

    static {
        ROLE_RULES.put("/api/auth/admin/**", "ADMIN");
        ROLE_RULES.put("/api/mentors/admin/**", "ADMIN");
        ROLE_RULES.put("/api/auth/internal/**", "NOBODY");
        ROLE_RULES.put("/api/payments/admin/**", "ADMIN");
        ROLE_RULES.put("/api/ai/admin/**", "ADMIN");
        ROLE_RULES.put("/api/ai/mentors/**", "ADMIN");
    }

    private record Identity(Long userId, String email, String role) {
    }

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;
    private final SecretKey signingKey;

    public JwtAuthenticationFilter(ObjectMapper objectMapper, @Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.objectMapper = objectMapper;
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest original = exchange.getRequest();
        String path = original.getPath().value();
        String correlationId = original.getHeaders().getFirst(CorrelationIdFilter.HEADER);

        // Never trust identity headers sent by a client
        ServerHttpRequest.Builder builder = original.mutate().headers(headers -> {
            headers.remove(USER_ID_HEADER);
            headers.remove(USER_EMAIL_HEADER);
            headers.remove(USER_ROLE_HEADER);
        });

        if (HttpMethod.OPTIONS.equals(original.getMethod()) || isPublic(path)) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String token = extractBearerToken(original);
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Authentication is required");
        }

        Identity identity;
        try {
            identity = authenticate(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("[{}] Rejected JWT for {}: {}", correlationId, path, ex.getClass().getSimpleName());
            return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token is invalid or expired");
        }

        for (Map.Entry<String, String> rule : ROLE_RULES.entrySet()) {
            if (pathMatcher.match(rule.getKey(), path) && !rule.getValue().equals(identity.role())) {
                log.warn("[{}] Access denied: userId={} role={} path={}",
                        correlationId, identity.userId(), identity.role(), path);
                return reject(exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                        "You do not have permission to perform this action");
            }
        }

        builder.header(USER_ID_HEADER, String.valueOf(identity.userId()));
        builder.header(USER_EMAIL_HEADER, identity.email());
        builder.header(USER_ROLE_HEADER, identity.role());
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private Identity authenticate(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        Long userId = claims.get("userId", Long.class);
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        if (userId == null || email == null || role == null) {
            throw new JwtException("Token is missing required claims");
        }
        return new Identity(userId, email, role);
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(
                    ErrorResponse.of(status, code, message, exchange.getRequest().getPath().value()));
        } catch (JsonProcessingException ex) {
            bytes = "{}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}