package org.IAM.services;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.IAM.dto.ProfileResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProfileCacheService {

    private static final String PROFILE_KEY_PREFIX = "profile:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long cacheTtlSeconds;

    public ProfileCacheService(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        @Value("${app.cache.profile-ttl-seconds:600}") long cacheTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public Optional<ProfileResponse> get(UUID userId) {
        String cachedJson = redisTemplate.opsForValue().get(PROFILE_KEY_PREFIX + userId);
        if (cachedJson == null || cachedJson.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cachedJson, ProfileResponse.class));
        } catch (JacksonException ex) {
            // Treat corrupt cache entries as a miss and evict them so PostgreSQL repopulates.
            evict(userId);
            return Optional.empty();
        }
    }

    public void put(UUID userId, ProfileResponse profile) {
        try {
            String json = objectMapper.writeValueAsString(profile);
            redisTemplate.opsForValue().set(
                PROFILE_KEY_PREFIX + userId,
                json,
                Duration.ofSeconds(cacheTtlSeconds)
            );
        } catch (JacksonException ex) {
            // Cache write failures should not break profile reads.
        }
    }

    public void evict(UUID userId) {
        redisTemplate.delete(PROFILE_KEY_PREFIX + userId);
    }
}
