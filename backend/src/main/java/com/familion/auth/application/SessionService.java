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
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService implements SessionIssuer {

    private final UserAccountApi userAccountApi;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTtl;
    private final Clock clock;

    public SessionService(
            UserAccountApi userAccountApi,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenRepository refreshTokenRepository,
            SecurityProperties securityProperties,
            Clock clock) {
        this.userAccountApi = userAccountApi;
        this.accessTokenIssuer = accessTokenIssuer;
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
