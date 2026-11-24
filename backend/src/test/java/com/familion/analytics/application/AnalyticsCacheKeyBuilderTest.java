package com.familion.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.View;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticsCacheKeyBuilderTest {

    @Test
    void hashIsDeterministicRegardlessOfParameterInsertionOrder() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Map<String, String> orderA = new LinkedHashMap<>();
        orderA.put("lens", "NOMINAL");
        orderA.put("date", "2026-11-23");
        orderA.put("granularity", "MONTH");

        Map<String, String> orderB = new LinkedHashMap<>();
        orderB.put("granularity", "MONTH");
        orderB.put("lens", "NOMINAL");
        orderB.put("date", "2026-11-23");

        String hashA = AnalyticsCacheKeyBuilder.hash("summary", orderA, userId, View.PERSONAL);
        String hashB = AnalyticsCacheKeyBuilder.hash("summary", orderB, userId, View.PERSONAL);

        assertThat(hashA).hasSize(64).isEqualTo(hashB);
    }

    @Test
    void hashChangesWhenEndpointViewOrUserDiffers() {
        UUID user1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID user2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Map<String, String> params = Map.of("date", "2026-11-23");

        String base = AnalyticsCacheKeyBuilder.hash("summary", params, user1, View.PERSONAL);
        String diffEndpoint = AnalyticsCacheKeyBuilder.hash("series", params, user1, View.PERSONAL);
        String diffView = AnalyticsCacheKeyBuilder.hash("summary", params, user1, View.FAMILY);
        String diffUser = AnalyticsCacheKeyBuilder.hash("summary", params, user2, View.PERSONAL);

        assertThat(base).isNotEqualTo(diffEndpoint);
        assertThat(base).isNotEqualTo(diffView);
        assertThat(base).isNotEqualTo(diffUser);
    }
}
