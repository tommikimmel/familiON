package com.familion.notifications.infrastructure.web;

public record SystemVersionResponse(
        String version,
        LatestReleaseResponse latestRelease) {}
