package com.familion.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed JWT revocation list using keys {@code auth:revoked:{jti}} with TTL equal to the
 * token's remaining lifetime.
 *
 * <p>Documented fail-open policy: if Redis is unreachable during revocation checks, a warning is
 * logged and {@link #isRevoked(UUID)} returns {@code false} so active traffic is not blocked by
 * cache outages.
 */
@Service
public class RedisTokenRevocationService implements TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationService.class);
    static final String KEY_PREFIX = "auth:revoked:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisTokenRevocationService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public void revoke(UUID jti, Instant expiresAt) {
        Objects.requireNonNull(jti, "jti must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(jti), "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to record revoked token {} in Redis: {}", jti, ex.getMessage());
        }
    }

    @Override
    public boolean isRevoked(UUID jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
        } catch (RuntimeException ex) {
            log.warn(
                    "Redis unavailable while checking revocation for token {} (fail-open): {}",
                    jti,
                    ex.getMessage());
            return false;
        }
    }

    private static String key(UUID jti) {
        return KEY_PREFIX + jti;
    }
}
