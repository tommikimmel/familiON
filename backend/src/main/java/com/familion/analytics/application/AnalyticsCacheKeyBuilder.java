package com.familion.analytics.application;

import com.familion.common.security.View;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AnalyticsCacheKeyBuilder {

    private AnalyticsCacheKeyBuilder() {}

    public static String hash(
            String endpoint, Map<String, String> params, UUID userId, View view) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(view, "view must not be null");

        TreeMap<String, String> sorted = new TreeMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                if (k != null && v != null) {
                    sorted.put(k, v);
                }
            });
        }
        String canonicalParams = sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String raw = endpoint + "|" + canonicalParams + "|" + userId + "|" + view.name();
        return sha256Hex(raw);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
