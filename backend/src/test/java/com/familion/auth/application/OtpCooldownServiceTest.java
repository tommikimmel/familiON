package com.familion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.familion.auth.domain.EmailCodePurpose;
import com.familion.common.error.TooManyRequestsException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OtpCooldownServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private OtpCooldownService cooldownService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        this.redisTemplate = mock(StringRedisTemplate.class);
        this.valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        this.cooldownService = new OtpCooldownService(redisTemplate);
    }

    @Test
    void acquiresCooldownKeyAndRejectsSecondCallWithRetryAfter() {
        UUID userId = UUID.randomUUID();
        String key = "auth:otp-cooldown:" + userId + ":TWO_FACTOR_LOGIN";

        when(valueOps.setIfAbsent(key, "1", Duration.ofSeconds(60)))
                .thenReturn(Boolean.TRUE)
                .thenReturn(Boolean.FALSE);
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(42L);

        assertThatCode(() -> cooldownService.acquireOrThrow(userId, EmailCodePurpose.TWO_FACTOR_LOGIN))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> cooldownService.acquireOrThrow(userId, EmailCodePurpose.TWO_FACTOR_LOGIN))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).getRetryAfter())
                        .isEqualTo(Duration.ofSeconds(42)));
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        UUID userId = UUID.randomUUID();
        String key = "auth:otp-cooldown:" + userId + ":EMAIL_VERIFICATION";

        when(valueOps.setIfAbsent(key, "1", Duration.ofSeconds(60)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatCode(() -> cooldownService.acquireOrThrow(userId, EmailCodePurpose.EMAIL_VERIFICATION))
                .doesNotThrowAnyException();
    }
}
