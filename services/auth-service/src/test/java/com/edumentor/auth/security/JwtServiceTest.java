package com.edumentor.auth.security;

import com.edumentor.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes";

    private final UserPrincipal principal = new UserPrincipal(7L, "a@b.com", "hash", Role.MENTOR, true);

    @Test
    void generatedTokenContainsUserIdEmailAndRole() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        Claims claims = jwtService.parseClaims(jwtService.generateToken(principal));

        assertThat(claims.get("userId", Long.class)).isEqualTo(7L);
        assertThat(claims.get("email", String.class)).isEqualTo("a@b.com");
        assertThat(claims.get("role", String.class)).isEqualTo("MENTOR");
    }

    @Test
    void validTokenPassesValidation() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        assertThat(jwtService.isTokenValid(jwtService.generateToken(principal), principal)).isTrue();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, -1_000);
        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isTokenValid(token, principal)).isFalse();
        assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = new JwtService("another-secret-key-that-is-at-least-32-bytes", 60_000)
                .generateToken(principal);

        assertThat(new JwtService(SECRET, 60_000).isTokenValid(token, principal)).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken(principal) + "x";

        assertThat(jwtService.isTokenValid(token, principal)).isFalse();
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", 60_000))
                .isInstanceOf(IllegalStateException.class);
    }
}