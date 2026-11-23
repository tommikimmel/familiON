package com.familion.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "familion")
public record BffProperties(
        String coreUrl,
        boolean cookieSecure,
        int rateLimitPerMin
) {
    public BffProperties {
        if (coreUrl == null || coreUrl.isBlank()) {
            coreUrl = "http://localhost:8081";
        } else if (coreUrl.endsWith("/")) {
            coreUrl = coreUrl.substring(0, coreUrl.length() - 1);
        }
        if (rateLimitPerMin <= 0) {
            rateLimitPerMin = 120;
        }
    }
}
