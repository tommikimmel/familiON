package com.familion.family.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "familion.app")
public record FamilyAppProperties(String publicUrl, String mailFrom) {}
