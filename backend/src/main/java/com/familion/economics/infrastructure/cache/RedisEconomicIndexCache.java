package com.familion.economics.infrastructure.cache;

import com.familion.economics.application.EconomicIndexCache;
import com.familion.economics.application.IndexEntry;
import com.familion.economics.domain.IndexCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed cache for economic indices using {@link StringRedisTemplate} and JSON payloads.
 *
 * <p>Keys:
 * <ul>
 *   <li>{@code econ:latest:{indexCode}} with 30-minute TTL
 *   <li>{@code econ:history:{indexCode}:{yyyy-MM}} with 24-hour TTL
 * </ul>
 *
 * <p>All Redis errors are caught and logged at WARN so callers transparently fall back to
 * PostgreSQL when Redis is unavailable.
 */
@Component
public class RedisEconomicIndexCache implements EconomicIndexCache {

    public static final Duration LATEST_TTL = Duration.ofMinutes(30);
    public static final Duration HISTORY_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(RedisEconomicIndexCache.class);
    private static final String NULL_SENTINEL = "null";
    private static final int SCAN_BATCH_SIZE = 100;
    private static final TypeReference<List<IndexEntry>> ENTRY_LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisEconomicIndexCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public static String latestKey(IndexCode indexCode) {
        return "econ:latest:" + indexCode.name();
    }

    public static String historyKey(IndexCode indexCode, YearMonth yearMonth) {
        return "econ:history:" + indexCode.name() + ":" + yearMonth;
    }

    public static String historyPattern(IndexCode indexCode) {
        return "econ:history:" + indexCode.name() + ":*";
    }

    @Override
    public LatestLookup getLatest(IndexCode indexCode) {
        String key = latestKey(indexCode);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return new LatestLookup.Miss();
            }
            if (NULL_SENTINEL.equals(raw)) {
                return new LatestLookup.Hit(null);
            }
            return new LatestLookup.Hit(objectMapper.readValue(raw, IndexEntry.class));
        } catch (Exception ex) {
            log.warn("Redis read failed for key {}: {}", key, ex.getMessage());
            return new LatestLookup.Miss();
        }
    }

    @Override
    public void putLatest(IndexCode indexCode, IndexEntry value) {
        String key = latestKey(indexCode);
        try {
            String payload = value == null ? NULL_SENTINEL : objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, LATEST_TTL);
        } catch (Exception ex) {
            log.warn("Redis write failed for key {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public Optional<List<IndexEntry>> getMonthlyHistory(IndexCode indexCode, YearMonth yearMonth) {
        String key = historyKey(indexCode, yearMonth);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, ENTRY_LIST_TYPE));
        } catch (Exception ex) {
            log.warn("Redis read failed for key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putMonthlyHistory(
            IndexCode indexCode, YearMonth yearMonth, List<IndexEntry> entries) {
        String key = historyKey(indexCode, yearMonth);
        try {
            String payload = objectMapper.writeValueAsString(entries);
            redisTemplate.opsForValue().set(key, payload, HISTORY_TTL);
        } catch (Exception ex) {
            log.warn("Redis write failed for key {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void invalidate(IndexCode indexCode, Set<YearMonth> affectedMonths) {
        List<String> keys = new ArrayList<>();
        keys.add(latestKey(indexCode));
        if (affectedMonths != null) {
            for (YearMonth month : affectedMonths) {
                keys.add(historyKey(indexCode, month));
            }
        }
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException ex) {
            log.warn(
                    "Redis invalidation failed for index {}: {}", indexCode, ex.getMessage());
        }
    }

    @Override
    public void invalidateAllFor(IndexCode indexCode) {
        try {
            List<String> keysToDelete = new ArrayList<>();
            keysToDelete.add(latestKey(indexCode));
            ScanOptions options = ScanOptions.scanOptions()
                    .match(historyPattern(indexCode))
                    .count(SCAN_BATCH_SIZE)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                }
            }
            redisTemplate.delete(keysToDelete);
        } catch (RuntimeException ex) {
            log.warn(
                    "Redis full invalidation failed for index {}: {}", indexCode, ex.getMessage());
        }
    }
}
