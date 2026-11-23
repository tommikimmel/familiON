package com.familion.common.cache;

import java.util.Objects;
import java.util.UUID;

public final class CacheKeys {

    private CacheKeys() {}

    public static String analytics(UUID familyId, String hash) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(hash, "hash must not be null");
        return "family:" + familyId + ":analytics:" + hash;
    }

    public static String analyticsPattern(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        return "family:" + familyId + ":analytics:*";
    }

    public static String categories(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        return "family:" + familyId + ":categories";
    }
}
