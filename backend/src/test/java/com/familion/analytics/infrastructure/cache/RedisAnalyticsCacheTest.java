package com.familion.analytics.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.analytics.application.AnalyticsSummary;
import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsPeriod;
import com.familion.common.cache.CacheKeys;
import com.familion.common.security.View;
import com.familion.common.web.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@ExtendWith(MockitoExtension.class)
class RedisAnalyticsCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private RedisAnalyticsCache cache;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().familionJacksonCustomizer().customize(builder);
        objectMapper = builder.build();
        cache = new RedisAnalyticsCache(redisTemplate, objectMapper);
    }

    @Test
    void storesAndReadsJsonWithFiveMinuteTtlAndPreservesBigDecimalAndNulls() {
        UUID familyId = UUID.randomUUID();
        String hash = "abc123";
        String expectedKey = CacheKeys.analytics(familyId, hash);

        AnalyticsSummary summary = new AnalyticsSummary(
                new AnalyticsPeriod(
                        AnalyticsGranularity.MONTH,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 30),
                        "Noviembre 2026"),
                View.PERSONAL,
                AnalyticsLens.NOMINAL,
                "ARS",
                new BigDecimal("100000.0000"),
                new BigDecimal("45000.5000"),
                new BigDecimal("54999.5000"),
                new BigDecimal("54.9995"),
                null,
                new AnalyticsSummary.PreviousPeriodTotals(
                        new BigDecimal("80000.0000"), new BigDecimal("30000.0000")),
                new BigDecimal("12.3456"),
                false);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cache.put(familyId, hash, summary);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(expectedKey), payloadCaptor.capture(), eq(RedisAnalyticsCache.TTL));
        assertThat(RedisAnalyticsCache.TTL).hasMinutes(5);

        when(valueOps.get(expectedKey)).thenReturn(payloadCaptor.getValue());
        Optional<AnalyticsSummary> restored = cache.get(familyId, hash, AnalyticsSummary.class);

        assertThat(restored).contains(summary);
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        UUID familyId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOps)
                .set(anyString(), anyString(), any());

        assertThat(cache.get(familyId, "h1", AnalyticsSummary.class)).isEmpty();
        cache.put(familyId, "h1", new AnalyticsSummary.PreviousPeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
