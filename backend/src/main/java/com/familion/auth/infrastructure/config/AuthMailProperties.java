package com.familion.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "familion.app")
public record AuthMailProperties(String publicUrl, String mailFrom) {

    public String normalizedPublicUrl() {
        if (publicUrl == null || publicUrl.isBlank()) {
            return "";
        }
        String trimmed = publicUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
