package com.familion.auth.application;

import com.familion.auth.application.api.SessionIssuer;
import com.familion.auth.application.api.SessionTokens;
import com.familion.auth.domain.AuthUnauthorizedException;
import com.familion.auth.domain.RefreshToken;
import com.familion.auth.domain.RefreshTokenCrypto;
import com.familion.auth.infrastructure.persistence.RefreshTokenRepository;
import com.familion.common.security.AccessTokenIssuer;
import com.familion.common.security.IssuedAccessToken;
import com.familion.common.security.SecurityProperties;
import com.familion.common.security.TokenRevocationService;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService implements SessionIssuer {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid or expired refresh token";

    private final UserAccountApi userAccountApi;
    private final AccessTokenIssuer accessTokenIssuer;
    private final TokenRevocationService tokenRevocationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTtl;
    private final Clock clock;

    public SessionService(
            UserAccountApi userAccountApi,
            AccessTokenIssuer accessTokenIssuer,
            TokenRevocationService tokenRevocationService,
            RefreshTokenRepository refreshTokenRepository,
            SecurityProperties securityProperties,
            Clock clock) {
        this.userAccountApi = userAccountApi;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenRevocationService = tokenRevocationService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTtl = Objects.requireNonNull(
                securityProperties.refreshTtl(), "familion.security.refresh-ttl must not be null");
        if (!this.refreshTtl.isPositive()) {
            throw new IllegalArgumentException("familion.security.refresh-ttl must be positive");
        }
        this.clock = clock;
    }

    @Override
    @Transactional
    public SessionTokens issueSession(UUID userId, String deviceInfo) {
        Objects.requireNonNull(userId, "userId must not be null");
        UserAccount user = userAccountApi.require(userId);
        if (!user.active()) {
            throw new AuthUnauthorizedException("Invalid credentials");
        }
        return issueTokensForAccount(user, deviceInfo);
    }

    @Override
    @Transactional
    public void revokeAllSessions(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        refreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant());
    }

    @Transactional(noRollbackFor = AuthUnauthorizedException.class)
    public SessionTokens refresh(String rawRefreshToken, String deviceInfo) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthUnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        String tokenHash = RefreshTokenCrypto.sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthUnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));

        Instant now = clock.instant();
        if (stored.isRevoked()) {
            log.warn(
                    "Refresh token reuse detected for user {}; revoking all active sessions",
                    stored.getUserId());
            refreshTokenRepository.revokeAllActiveByUserId(stored.getUserId(), now);
            throw new AuthUnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (stored.isExpired(now)) {
            throw new AuthUnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        int revokedCount = refreshTokenRepository.revokeIfActive(stored.getId(), now);
        if (revokedCount == 0) {
            log.warn(
                    "Concurrent refresh token reuse detected for user {}; revoking all active sessions",
                    stored.getUserId());
            refreshTokenRepository.revokeAllActiveByUserId(stored.getUserId(), now);
            throw new AuthUnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        UserAccount user = userAccountApi.require(stored.getUserId());
        if (!user.active()) {
            refreshTokenRepository.revokeAllActiveByUserId(stored.getUserId(), now);
            throw new AuthUnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        String effectiveDeviceInfo =
                (deviceInfo != null && !deviceInfo.isBlank()) ? deviceInfo : stored.getDeviceInfo();
        return issueTokensForAccount(user, effectiveDeviceInfo);
    }

    @Transactional
    public void logout(
            UUID userId,
            UUID accessTokenJti,
            Instant accessTokenExpiresAt,
            String rawRefreshToken) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (accessTokenJti != null && accessTokenExpiresAt != null) {
            tokenRevocationService.revoke(accessTokenJti, accessTokenExpiresAt);
        }
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = RefreshTokenCrypto.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(stored -> {
            if (stored.getUserId().equals(userId) && !stored.isRevoked()) {
                stored.revoke(clock.instant());
                refreshTokenRepository.save(stored);
            }
        });
    }

    SessionTokens issueTokensForAccount(UserAccount user, String deviceInfo) {
        IssuedAccessToken accessToken =
                accessTokenIssuer.issue(user.id(), user.familyId(), user.role(), user.visibility());

        String rawRefreshToken = RefreshTokenCrypto.generateRawToken();
        String tokenHash = RefreshTokenCrypto.sha256Hex(rawRefreshToken);
        Instant expiresAt = clock.instant().plus(refreshTtl);

        RefreshToken refreshToken =
                RefreshToken.create(user.id(), tokenHash, expiresAt, deviceInfo);
        refreshTokenRepository.save(refreshToken);

        return new SessionTokens(
                false,
                null,
                accessToken.token(),
                accessToken.expiresInSeconds(),
                rawRefreshToken,
                refreshTtl.toSeconds(),
                null);
    }
}
