package org.IAM.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetStoreService {

    private static final String CODE_KEY_PREFIX = "reset:code:";
    private static final String ATTEMPTS_KEY_PREFIX = "reset:attempts:";

    private final StringRedisTemplate redisTemplate;
    private final long codeTtlSeconds;
    private final int maxAttempts;

    public PasswordResetStoreService(
        StringRedisTemplate redisTemplate,
        @Value("${app.password-reset.code-ttl-seconds:600}") long codeTtlSeconds,
        @Value("${app.password-reset.max-attempts:5}") int maxAttempts
    ) {
        this.redisTemplate = redisTemplate;
        this.codeTtlSeconds = codeTtlSeconds;
        this.maxAttempts = maxAttempts;
    }

    public void saveCode(String normalizedEmail, String code) {
        Duration ttl = Duration.ofSeconds(codeTtlSeconds);
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + normalizedEmail, code, ttl);
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + normalizedEmail);
    }

    public boolean verifyAndConsume(String normalizedEmail, String candidateCode) {
        String codeKey = CODE_KEY_PREFIX + normalizedEmail;
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || storedCode.isBlank()) {
            return false;
        }

        boolean matches = MessageDigest.isEqual(
            storedCode.getBytes(StandardCharsets.UTF_8),
            candidateCode.getBytes(StandardCharsets.UTF_8)
        );
        if (matches) {
            clear(normalizedEmail);
            return true;
        }

        String attemptsKey = ATTEMPTS_KEY_PREFIX + normalizedEmail;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, Duration.ofSeconds(codeTtlSeconds));
        }
        if (attempts != null && attempts >= maxAttempts) {
            clear(normalizedEmail);
        }
        return false;
    }

    public void clear(String normalizedEmail) {
        redisTemplate.delete(CODE_KEY_PREFIX + normalizedEmail);
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + normalizedEmail);
    }
}
