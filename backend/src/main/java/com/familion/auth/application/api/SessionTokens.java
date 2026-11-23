package com.familion.auth.application.api;

public record SessionTokens(
        boolean requires2fa,
        String challengeToken,
        String accessToken,
        Long accessTokenExpiresIn,
        String refreshToken,
        Long refreshTokenExpiresIn,
        String deviceToken) {}
