package com.familion.analytics.infrastructure.cache;

import com.familion.analytics.application.AnalyticsCache;
import com.familion.common.cache.CacheKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed cache for analytics responses under {@code family:{familyId}:analytics:{hash}} with
 * a 5-minute TTL. Fails open on Redis or serialization errors.
 */
@Component
public class RedisAnalyticsCache implements AnalyticsCache {

    public static final Duration TTL = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(RedisAnalyticsCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAnalyticsCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> Optional<T> get(UUID familyId, String hash, Class<T> type) {
        String key = CacheKeys.analytics(familyId, hash);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception ex) {
            log.warn("Analytics cache read failed for key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID familyId, String hash, Object value) {
        String key = CacheKeys.analytics(familyId, hash);
        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, TTL);
        } catch (Exception ex) {
            log.warn("Analytics cache write failed for key {}: {}", key, ex.getMessage());
        }
    }
}
