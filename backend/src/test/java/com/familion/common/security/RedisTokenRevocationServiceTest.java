package com.familion.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisTokenRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisTokenRevocationService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        this.redisTemplate = mock(StringRedisTemplate.class);
        this.valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        this.service = new RedisTokenRevocationService(redisTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesRevokedTokenWithRemainingTtl() {
        UUID jti = UUID.randomUUID();
        Instant expiresAt = NOW.plus(Duration.ofMinutes(10));

        service.revoke(jti, expiresAt);

        verify(valueOperations).set("auth:revoked:" + jti, "1", Duration.ofMinutes(10));
    }

    @Test
    void skipsRevocationWhenTokenAlreadyExpired() {
        UUID jti = UUID.randomUUID();

        service.revoke(jti, NOW.minusSeconds(1));
        service.revoke(jti, NOW);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void returnsTrueWhenKeyExistsInRedis() {
        UUID jti = UUID.randomUUID();
        when(redisTemplate.hasKey("auth:revoked:" + jti)).thenReturn(true);

        assertThat(service.isRevoked(jti)).isTrue();
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        UUID jti = UUID.randomUUID();
        when(redisTemplate.hasKey("auth:revoked:" + jti))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(service.isRevoked(jti)).isFalse();
    }
}
