package com.familion.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "familion.security")
public record SecurityProperties(String jwtSecret, Duration accessTtl, Duration refreshTtl) {}
