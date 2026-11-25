package org.IAM.services;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenStoreService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String BLOCKLIST_KEY_PREFIX = "blacklist:access:";

    private final StringRedisTemplate redisTemplate;
    private final long refreshExpirationSeconds;

    public TokenStoreService(
        StringRedisTemplate redisTemplate,
        @Value("${app.jwt.refresh-expiration-seconds:10800}") long refreshExpirationSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public void saveRefreshToken(String refreshToken, UUID userId) {
        redisTemplate.opsForValue().set(
            REFRESH_KEY_PREFIX + refreshToken,
            userId.toString(),
            Duration.ofSeconds(refreshExpirationSeconds)
        );
    }

    public Optional<UUID> findUserIdByRefreshToken(String refreshToken) {
        String storedUserId = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + refreshToken);
        if (storedUserId == null || storedUserId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(storedUserId));
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
    }

    public void blocklistAccessToken(String tokenId, Duration remainingTtl) {
        if (remainingTtl.isZero() || remainingTtl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(BLOCKLIST_KEY_PREFIX + tokenId, "revoked", remainingTtl);
    }

    public boolean isAccessTokenBlocklisted(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCKLIST_KEY_PREFIX + tokenId));
    }
}
