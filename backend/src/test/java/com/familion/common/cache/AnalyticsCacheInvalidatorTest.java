package com.familion.common.cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.familion.common.events.FamilyFinancialDataChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

class AnalyticsCacheInvalidatorTest {

    @Test
    void doesNotPropagateExceptionWhenRedisIsDown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.scan(any(ScanOptions.class)))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        AnalyticsCacheInvalidator invalidator = new AnalyticsCacheInvalidator(redisTemplate);

        assertThatCode(() -> invalidator.onFamilyFinancialDataChanged(
                        new FamilyFinancialDataChangedEvent(UUID.randomUUID())))
                .doesNotThrowAnyException();
    }
}
