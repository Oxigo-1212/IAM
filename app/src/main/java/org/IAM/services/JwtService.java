package org.IAM.services;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpirationSeconds;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.access-expiration-seconds:900}") long accessExpirationSeconds
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpirationSeconds = accessExpirationSeconds;
    }

    public String generateAccessToken(UUID userId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(accessExpirationSeconds);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    public JwtPayload parseAndValidate(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String tokenId = claims.getId();
        Instant expiresAt = claims.getExpiration().toInstant();
        return new JwtPayload(userId, tokenId, expiresAt);
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public record JwtPayload(UUID userId, String tokenId, Instant expiresAt) {}
}
