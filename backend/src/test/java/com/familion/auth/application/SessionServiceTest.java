package com.familion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.auth.application.api.SessionTokens;
import com.familion.auth.domain.AuthUnauthorizedException;
import com.familion.auth.domain.RefreshToken;
import com.familion.auth.domain.RefreshTokenCrypto;
import com.familion.auth.infrastructure.persistence.RefreshTokenRepository;
import com.familion.common.security.AccessTokenIssuer;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.IssuedAccessToken;
import com.familion.common.security.Role;
import com.familion.common.security.SecurityProperties;
import com.familion.common.security.TokenRevocationService;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-14T12:00:00Z");

    private UserAccountApi userAccountApi;
    private AccessTokenIssuer accessTokenIssuer;
    private TokenRevocationService tokenRevocationService;
    private RefreshTokenRepository refreshTokenRepository;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        this.userAccountApi = mock(UserAccountApi.class);
        this.accessTokenIssuer = mock(AccessTokenIssuer.class);
        this.tokenRevocationService = mock(TokenRevocationService.class);
        this.refreshTokenRepository = mock(RefreshTokenRepository.class);
        SecurityProperties properties =
                new SecurityProperties("secret", Duration.ofMinutes(15), Duration.ofDays(30));
        this.sessionService = new SessionService(
                userAccountApi,
                accessTokenIssuer,
                tokenRevocationService,
                refreshTokenRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueSessionStoresOnlyHashedRefreshToken() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UserAccount user = activeUser(userId, familyId);
        when(userAccountApi.require(userId)).thenReturn(user);
        when(accessTokenIssuer.issue(userId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL))
                .thenReturn(new IssuedAccessToken(
                        "jwt-access", UUID.randomUUID(), NOW.plus(Duration.ofMinutes(15)), 900L));

        SessionTokens tokens = sessionService.issueSession(userId, "Chrome/125");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash())
                .isEqualTo(RefreshTokenCrypto.sha256Hex(tokens.refreshToken()))
                .isNotEqualTo(tokens.refreshToken());
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(saved.getDeviceInfo()).isEqualTo("Chrome/125");
        assertThat(tokens.requires2fa()).isFalse();
        assertThat(tokens.accessToken()).isEqualTo("jwt-access");
        assertThat(tokens.accessTokenExpiresIn()).isEqualTo(900L);
        assertThat(tokens.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(30).toSeconds());
        assertThat(tokens.deviceToken()).isNull();
    }

    @Test
    void refreshRevokesAllUserSessionsWhenReusedTokenIsPresented() {
        UUID userId = UUID.randomUUID();
        String rawToken = RefreshTokenCrypto.generateRawToken();
        String hash = RefreshTokenCrypto.sha256Hex(rawToken);
        RefreshToken revoked = RefreshToken.create(userId, hash, NOW.plusSeconds(3600), "UA");
        revoked.revoke(NOW.minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> sessionService.refresh(rawToken, "UA"))
                .isInstanceOf(AuthUnauthorizedException.class);

        verify(refreshTokenRepository).revokeAllActiveByUserId(userId, NOW);
    }

    @Test
    void refreshRejectsExpiredTokenWithoutRevokingOtherSessions() {
        UUID userId = UUID.randomUUID();
        String rawToken = RefreshTokenCrypto.generateRawToken();
        String hash = RefreshTokenCrypto.sha256Hex(rawToken);
        RefreshToken expired = RefreshToken.create(userId, hash, NOW.minusSeconds(1), "UA");
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> sessionService.refresh(rawToken, "UA"))
                .isInstanceOf(AuthUnauthorizedException.class);

        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void logoutRevokesJwtAndOnlyOwnedRefreshToken() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID jti = UUID.randomUUID();
        Instant expiresAt = NOW.plus(Duration.ofMinutes(10));

        String otherRaw = RefreshTokenCrypto.generateRawToken();
        String otherHash = RefreshTokenCrypto.sha256Hex(otherRaw);
        RefreshToken otherToken =
                RefreshToken.create(otherUserId, otherHash, NOW.plusSeconds(3600), "UA");
        when(refreshTokenRepository.findByTokenHash(otherHash)).thenReturn(Optional.of(otherToken));

        sessionService.logout(userId, jti, expiresAt, otherRaw);

        verify(tokenRevocationService).revoke(jti, expiresAt);
        verify(refreshTokenRepository, never()).save(any());
        assertThat(otherToken.isRevoked()).isFalse();
    }

    private static UserAccount activeUser(UUID userId, UUID familyId) {
        return new UserAccount(
                userId,
                familyId,
                "user@familion.test",
                "User",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                "{bcrypt}hash",
                true,
                true,
                true);
    }
}
