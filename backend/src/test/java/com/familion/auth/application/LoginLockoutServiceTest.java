package com.familion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginLockoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private LoginLockoutService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        this.redisTemplate = mock(StringRedisTemplate.class);
        this.valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        this.service = new LoginLockoutService(redisTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void computesExponentialBackoffCappedAtOneHour() {
        assertThat(LoginLockoutService.computeLockoutSeconds(4)).isZero();
        assertThat(LoginLockoutService.computeLockoutSeconds(5)).isEqualTo(60L);
        assertThat(LoginLockoutService.computeLockoutSeconds(6)).isEqualTo(120L);
        assertThat(LoginLockoutService.computeLockoutSeconds(7)).isEqualTo(240L);
        assertThat(LoginLockoutService.computeLockoutSeconds(8)).isEqualTo(480L);
        assertThat(LoginLockoutService.computeLockoutSeconds(9)).isEqualTo(960L);
        assertThat(LoginLockoutService.computeLockoutSeconds(10)).isEqualTo(1920L);
        assertThat(LoginLockoutService.computeLockoutSeconds(11)).isEqualTo(3600L);
        assertThat(LoginLockoutService.computeLockoutSeconds(20)).isEqualTo(3600L);
    }

    @Test
    void fifthFailureSetsLockoutAndNextAttemptThrowsTooManyRequestsWithRetryAfter() {
        String key = "auth:login-fail:user@familion.test";
        when(valueOperations.get(key)).thenReturn("4:0");

        service.recordFailure("USER@Familion.test");

        long expectedLockedUntil = NOW.plusSeconds(60).toEpochMilli();
        verify(valueOperations).set(key, "5:" + expectedLockedUntil, Duration.ofHours(1));

        when(valueOperations.get(key)).thenReturn("5:" + expectedLockedUntil);
        assertThatThrownBy(() -> service.assertNotLocked("user@familion.test"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).getRetryAfter())
                        .isEqualTo(Duration.ofSeconds(60)));
    }

    @Test
    void resetDeletesRedisKey() {
        service.reset("User@Familion.test");
        verify(redisTemplate).delete("auth:login-fail:user@familion.test");
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        doThrow(new RedisConnectionFailureException("connection refused"))
                .when(redisTemplate)
                .delete(anyString());

        assertThatCode(() -> service.assertNotLocked("user@familion.test")).doesNotThrowAnyException();
        assertThatCode(() -> service.recordFailure("user@familion.test")).doesNotThrowAnyException();
        assertThatCode(() -> service.reset("user@familion.test")).doesNotThrowAnyException();
    }
}
