package com.familion.bff.auth;

public record CoreTokenResponse(
        boolean requires2fa,
        String challengeToken,
        String accessToken,
        Long accessTokenExpiresIn,
        String refreshToken,
        Long refreshTokenExpiresIn,
        String deviceToken
) {
}
