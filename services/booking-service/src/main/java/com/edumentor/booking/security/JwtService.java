package com.edumentor.booking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Verifies signature and expiry and extracts the identity. Throws JwtException when invalid. */
    public AuthenticatedUser authenticate(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        Long userId = claims.get("userId", Long.class);
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        if (userId == null || email == null || role == null) {
            throw new JwtException("Token is missing required claims");
        }
        return new AuthenticatedUser(userId, email, role);
    }
}