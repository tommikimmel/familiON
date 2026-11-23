package com.familion.auth.application;

import com.familion.auth.domain.EmailCodePurpose;
import com.familion.common.error.TooManyRequestsException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Per-user, per-purpose resend cooldown backed by Redis key
 * {@code auth:otp-cooldown:{userId}:{purpose}} for 60 seconds. Fails open if Redis is unavailable.
 */
@Service
public class OtpCooldownService {

    private static final Logger log = LoggerFactory.getLogger(OtpCooldownService.class);
    static final String KEY_PREFIX = "auth:otp-cooldown:";
    static final Duration COOLDOWN_DURATION = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public OtpCooldownService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void acquireOrThrow(UUID userId, EmailCodePurpose purpose) {
        String key = key(userId, purpose);
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", COOLDOWN_DURATION);
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable while checking OTP cooldown for {}: {}", key, ex.getMessage());
            return;
        }

        if (Boolean.FALSE.equals(acquired)) {
            long retryAfterSeconds = COOLDOWN_DURATION.toSeconds();
            try {
                Long remaining = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (remaining != null && remaining > 0) {
                    retryAfterSeconds = remaining;
                }
            } catch (RuntimeException ex) {
                log.warn("Redis unavailable while reading OTP cooldown TTL for {}: {}", key, ex.getMessage());
            }
            throw new TooManyRequestsException(
                    "Please wait before requesting another security code.",
                    Duration.ofSeconds(retryAfterSeconds));
        }
    }

    static String key(UUID userId, EmailCodePurpose purpose) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        return KEY_PREFIX + userId + ":" + purpose.name();
    }
}
