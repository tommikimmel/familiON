package com.familion.finance.categories.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.cache.CacheKeys;
import com.familion.finance.categories.application.CategoryView;
import com.familion.finance.categories.domain.CategoryKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisCategoryCacheTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private RedisCategoryCache cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        objectMapper = new ObjectMapper();
        cache = new RedisCategoryCache(redisTemplate, objectMapper);
    }

    @Test
    void putAndGetRoundTripJsonWithOneHourTtl() throws Exception {
        UUID familyId = UUID.randomUUID();
        String key = CacheKeys.categories(familyId);
        CategoryView view = new CategoryView(
                UUID.randomUUID(), null, "Supermercado", CategoryKind.EXPENSE, "shopping-cart", "#10B981", true, "SUPERMARKET");
        List<CategoryView> list = List.of(view);
        String json = objectMapper.writeValueAsString(list);

        cache.put(familyId, list);
        verify(valueOperations).set(eq(key), eq(json), eq(Duration.ofHours(1)));

        when(valueOperations.get(key)).thenReturn(json);
        Optional<List<CategoryView>> cached = cache.get(familyId);
        assertThat(cached).isPresent();
        assertThat(cached.get()).containsExactly(view);
    }

    @Test
    void failsOpenWhenRedisIsUnavailableOrPayloadIsCorrupt() {
        UUID familyId = UUID.randomUUID();
        String key = CacheKeys.categories(familyId);

        when(valueOperations.get(key)).thenThrow(new RedisConnectionFailureException("Redis down"));
        assertThat(cache.get(familyId)).isEmpty();

        doReturn("{not-valid-json").when(valueOperations).get(key);
        assertThat(cache.get(familyId)).isEmpty();

        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOperations)
                .set(eq(key), any(), any(Duration.class));
        assertThatCode(() -> cache.put(familyId, List.of())).doesNotThrowAnyException();

        doThrow(new RedisConnectionFailureException("Redis down")).when(redisTemplate).delete(key);
        assertThatCode(() -> cache.evict(familyId)).doesNotThrowAnyException();
    }
}
