package com.familion.notifications.infrastructure.web;

import java.time.Instant;

public record LatestReleaseResponse(
        String version,
        String title,
        Instant releasedAt) {}
