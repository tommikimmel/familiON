package com.familion.auth.application;

import com.familion.common.error.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Progressive login lockout backed by Redis key {@code auth:login-fail:{email}}.
 *
 * <p>After 5 consecutive failed attempts, logins for the email are blocked for {@code 60s *
 * 2^(failures - 5)} capped at 1 hour. Fails open (logs a warning without blocking) if Redis is
 * unavailable.
 */
@Service
public class LoginLockoutService {

    private static final Logger log = LoggerFactory.getLogger(LoginLockoutService.class);
    static final String KEY_PREFIX = "auth:login-fail:";
    static final int LOCKOUT_THRESHOLD = 5;
    static final long BASE_LOCKOUT_SECONDS = 60L;
    static final long MAX_LOCKOUT_SECONDS = 3600L;
    static final Duration STATE_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public LoginLockoutService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public void assertNotLocked(String email) {
        String key = key(email);
        String raw;
        try {
            raw = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable while checking login lockout for {}: {}", key, ex.getMessage());
            return;
        }
        LockoutState state = LockoutState.parse(raw);
        if (state == null || state.lockedUntilEpochMilli() <= 0L) {
            return;
        }
        long nowMillis = clock.instant().toEpochMilli();
        if (state.lockedUntilEpochMilli() > nowMillis) {
            long remainingMillis = state.lockedUntilEpochMilli() - nowMillis;
            long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again later.",
                    Duration.ofSeconds(remainingSeconds));
        }
    }

    public void recordFailure(String email) {
        String key = key(email);
        try {
            LockoutState previous = LockoutState.parse(redisTemplate.opsForValue().get(key));
            int failures = (previous == null ? 0 : previous.failures()) + 1;
            long lockedUntilEpochMilli = 0L;
            if (failures >= LOCKOUT_THRESHOLD) {
                long lockoutSeconds = computeLockoutSeconds(failures);
                Instant lockedUntil = clock.instant().plusSeconds(lockoutSeconds);
                lockedUntilEpochMilli = lockedUntil.toEpochMilli();
            }
            redisTemplate.opsForValue().set(key, LockoutState.format(failures, lockedUntilEpochMilli), STATE_TTL);
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable while recording failed login for {}: {}", key, ex.getMessage());
        }
    }

    public void reset(String email) {
        String key = key(email);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable while resetting login lockout for {}: {}", key, ex.getMessage());
        }
    }

    static long computeLockoutSeconds(int failures) {
        if (failures < LOCKOUT_THRESHOLD) {
            return 0L;
        }
        int exponent = Math.min(failures - LOCKOUT_THRESHOLD, 10);
        return Math.min(MAX_LOCKOUT_SECONDS, BASE_LOCKOUT_SECONDS * (1L << exponent));
    }

    private static String key(String email) {
        Objects.requireNonNull(email, "email must not be null");
        return KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
    }

    private record LockoutState(int failures, long lockedUntilEpochMilli) {

        static String format(int failures, long lockedUntilEpochMilli) {
            return failures + ":" + lockedUntilEpochMilli;
        }

        static LockoutState parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            try {
                return new LockoutState(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
