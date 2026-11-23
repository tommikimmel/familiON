package com.familion.economics.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.economics.application.EconomicIndexCache.LatestLookup;
import com.familion.economics.application.IndexEntry;
import com.familion.economics.domain.IndexCode;
import com.familion.economics.domain.IndexSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisEconomicIndexCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private RedisEconomicIndexCache cache;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        cache = new RedisEconomicIndexCache(redisTemplate, objectMapper);
    }

    @Test
    void storesAndReadsLatestWith30MinuteTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        IndexEntry entry = new IndexEntry(
                IndexCode.USD_MEP,
                LocalDate.of(2026, 11, 23),
                new BigDecimal("1743.100000"),
                IndexSource.API);

        cache.putLatest(IndexCode.USD_MEP, entry);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps)
                .set(
                        eq("econ:latest:USD_MEP"),
                        jsonCaptor.capture(),
                        eq(Duration.ofMinutes(30)));

        when(valueOps.get("econ:latest:USD_MEP")).thenReturn(jsonCaptor.getValue());
        LatestLookup lookup = cache.getLatest(IndexCode.USD_MEP);

        assertThat(lookup).isInstanceOf(LatestLookup.Hit.class);
        assertThat(((LatestLookup.Hit) lookup).value()).isEqualTo(entry);
    }

    @Test
    void storesAndReadsMonthlyHistoryWith24HourTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        YearMonth month = YearMonth.of(2026, 11);
        List<IndexEntry> entries = List.of(
                new IndexEntry(
                        IndexCode.UVA,
                        LocalDate.of(2026, 11, 20),
                        new BigDecimal("2205.170000"),
                        IndexSource.API));

        cache.putMonthlyHistory(IndexCode.UVA, month, entries);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps)
                .set(
                        eq("econ:history:UVA:2026-11"),
                        jsonCaptor.capture(),
                        eq(Duration.ofHours(24)));

        when(valueOps.get("econ:history:UVA:2026-11")).thenReturn(jsonCaptor.getValue());
        Optional<List<IndexEntry>> read = cache.getMonthlyHistory(IndexCode.UVA, month);

        assertThat(read).contains(entries);
    }

    @Test
    void invalidatesLatestAndAffectedMonthlyHistoryKeys() {
        cache.invalidate(
                IndexCode.USD_BLUE, Set.of(YearMonth.of(2026, 10), YearMonth.of(2026, 11)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertThat(keysCaptor.getValue())
                .containsExactlyInAnyOrder(
                        "econ:latest:USD_BLUE",
                        "econ:history:USD_BLUE:2026-10",
                        "econ:history:USD_BLUE:2026-11");
    }

    @Test
    void degradesGracefullyWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue())
                .thenThrow(new RedisConnectionFailureException("Redis down"));
        when(redisTemplate.delete(anyList()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThat(cache.getLatest(IndexCode.USD_MEP)).isEqualTo(new LatestLookup.Miss());
        assertThat(cache.getMonthlyHistory(IndexCode.USD_MEP, YearMonth.of(2026, 11))).isEmpty();
        assertThatCode(() -> cache.putLatest(IndexCode.USD_MEP, null)).doesNotThrowAnyException();
        assertThatCode(() -> cache.putMonthlyHistory(IndexCode.USD_MEP, YearMonth.of(2026, 11), List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> cache.invalidate(IndexCode.USD_MEP, Set.of(YearMonth.of(2026, 11))))
                .doesNotThrowAnyException();
    }
}
