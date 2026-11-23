package com.familion.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.test.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class AnalyticsCacheInvalidatorIT extends IntegrationTestBase {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void deletesOnlyTargetFamilyAnalyticsKeysAfterTransactionCommit() {
        UUID familyA = UUID.randomUUID();
        UUID familyB = UUID.randomUUID();

        String familyAAnalytics1 = CacheKeys.analytics(familyA, "monthly-2026-05");
        String familyAAnalytics2 = CacheKeys.analytics(familyA, "yearly-2026");
        String familyACategories = CacheKeys.categories(familyA);
        String familyBAnalytics = CacheKeys.analytics(familyB, "monthly-2026-05");

        redisTemplate.opsForValue().set(familyAAnalytics1, "{\"total\":100}");
        redisTemplate.opsForValue().set(familyAAnalytics2, "{\"total\":1200}");
        redisTemplate.opsForValue().set(familyACategories, "[\"food\"]");
        redisTemplate.opsForValue().set(familyBAnalytics, "{\"total\":50}");

        transactionTemplate.executeWithoutResult(
                status -> eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyA)));

        assertThat(redisTemplate.hasKey(familyAAnalytics1)).isFalse();
        assertThat(redisTemplate.hasKey(familyAAnalytics2)).isFalse();
        assertThat(redisTemplate.hasKey(familyACategories)).isTrue();
        assertThat(redisTemplate.hasKey(familyBAnalytics)).isTrue();

        redisTemplate.delete(familyACategories);
        redisTemplate.delete(familyBAnalytics);
    }

    @Test
    void keepsAnalyticsKeysIntactWhenTransactionRollsBack() {
        UUID familyId = UUID.randomUUID();
        String analyticsKey = CacheKeys.analytics(familyId, "monthly-rollback");
        redisTemplate.opsForValue().set(analyticsKey, "{\"total\":250}");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
            status.setRollbackOnly();
        });

        assertThat(redisTemplate.hasKey(analyticsKey)).isTrue();
        redisTemplate.delete(analyticsKey);
    }
}
