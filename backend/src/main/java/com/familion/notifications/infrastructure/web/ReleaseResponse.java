package com.familion.notifications.infrastructure.web;

import java.time.Instant;

public record ReleaseResponse(
        String version,
        String title,
        String changelogMarkdown,
        boolean isBreaking,
        Instant releasedAt) {}
