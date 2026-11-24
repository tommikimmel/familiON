package com.familion.analytics.application;

import java.util.Optional;
import java.util.UUID;

public interface AnalyticsCache {

    <T> Optional<T> get(UUID familyId, String hash, Class<T> type);

    void put(UUID familyId, String hash, Object value);
}
