package com.familion.finance.categories.infrastructure.cache;

import com.familion.common.cache.CacheKeys;
import com.familion.finance.categories.application.CategoryCache;
import com.familion.finance.categories.application.CategoryView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed cache for family category listings under {@code family:{familyId}:categories}
 * with a 1-hour TTL. Fails open on Redis or serialization errors.
 */
@Component
public class RedisCategoryCache implements CategoryCache {

    public static final Duration TTL = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(RedisCategoryCache.class);
    private static final TypeReference<List<CategoryView>> CATEGORY_LIST_TYPE =
            new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCategoryCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<List<CategoryView>> get(UUID familyId) {
        String key = CacheKeys.categories(familyId);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, CATEGORY_LIST_TYPE));
        } catch (Exception ex) {
            log.warn("Redis read failed for key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID familyId, List<CategoryView> categories) {
        String key = CacheKeys.categories(familyId);
        try {
            String payload = objectMapper.writeValueAsString(categories);
            redisTemplate.opsForValue().set(key, payload, TTL);
        } catch (Exception ex) {
            log.warn("Redis write failed for key {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void evict(UUID familyId) {
        String key = CacheKeys.categories(familyId);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis eviction failed for key {}: {}", key, ex.getMessage());
        }
    }
}
