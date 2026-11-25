package org.IAM.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-1234";

    @Test
    void generatesAndValidatesHs256AccessToken() {
        JwtService jwtService = new JwtService(SECRET, 900);
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId);
        JwtService.JwtPayload payload = jwtService.parseAndValidate(token);

        assertEquals(userId, payload.userId());
        assertNotNull(payload.tokenId());
        assertTrue(payload.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService(SECRET, 900);
        JwtService verifier = new JwtService("another-secret-key-at-least-32-bytes-5678", 900);

        String token = issuer.generateAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> verifier.parseAndValidate(token));
    }
}
