package org.IAM.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PasswordResetStoreServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private PasswordResetStoreService storeService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        storeService = new PasswordResetStoreService(redisTemplate, 600, 5);
    }

    @Test
    void saveCodeStoresWithTtlAndClearsAttempts() {
        storeService.saveCode("alice@gmail.com", "123456");

        verify(valueOps).set("reset:code:alice@gmail.com", "123456", Duration.ofSeconds(600));
        verify(redisTemplate).delete("reset:attempts:alice@gmail.com");
    }

    @Test
    void verifyAndConsumeDeletesCodeOnMatch() {
        when(valueOps.get("reset:code:alice@gmail.com")).thenReturn("123456");

        assertTrue(storeService.verifyAndConsume("alice@gmail.com", "123456"));
        verify(redisTemplate).delete("reset:code:alice@gmail.com");
        verify(redisTemplate).delete("reset:attempts:alice@gmail.com");
    }

    @Test
    void verifyAndConsumeClearsCodeAfterMaxFailedAttempts() {
        when(valueOps.get("reset:code:alice@gmail.com")).thenReturn("123456");
        when(valueOps.increment("reset:attempts:alice@gmail.com")).thenReturn(5L);

        assertFalse(storeService.verifyAndConsume("alice@gmail.com", "999999"));
        verify(redisTemplate).delete("reset:code:alice@gmail.com");
        verify(redisTemplate).delete("reset:attempts:alice@gmail.com");
    }
}
