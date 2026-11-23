package com.familion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.auth.domain.EmailCodePurpose;
import com.familion.auth.domain.SecurityCodeCrypto;
import com.familion.auth.domain.TrustedDevice;
import com.familion.auth.infrastructure.persistence.TrustedDeviceRepository;
import com.familion.common.security.AccessTokenIssuer;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.api.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecondFactorGateImplTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private TrustedDeviceRepository trustedDeviceRepository;
    private EmailCodeService emailCodeService;
    private AuthMailSender authMailSender;
    private AccessTokenIssuer accessTokenIssuer;
    private RequestIpProvider requestIpProvider;
    private SecondFactorGateImpl gate;

    @BeforeEach
    void setUp() {
        this.trustedDeviceRepository = mock(TrustedDeviceRepository.class);
        this.emailCodeService = mock(EmailCodeService.class);
        this.authMailSender = mock(AuthMailSender.class);
        this.accessTokenIssuer = mock(AccessTokenIssuer.class);
        this.requestIpProvider = mock(RequestIpProvider.class);
        this.gate = new SecondFactorGateImpl(
                trustedDeviceRepository,
                emailCodeService,
                authMailSender,
                accessTokenIssuer,
                requestIpProvider,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void skipsChallengeWhenTwoFactorIsDisabled() {
        UserAccount user = user(UUID.randomUUID(), false);

        Optional<String> result = gate.challengeIfRequired(user, null);

        assertThat(result).isEmpty();
        verify(emailCodeService, never()).issueCode(any(), any(), any());
    }

    @Test
    void skipsChallengeWhenValidTrustedDeviceTokenMatchesUser() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user(userId, true);
        String rawDeviceToken = SecurityCodeCrypto.generateSecureToken();
        String hash = SecurityCodeCrypto.sha256Hex(rawDeviceToken);
        TrustedDevice device = TrustedDevice.create(userId, hash, "MacBook", NOW.plus(Duration.ofDays(10)));
        when(trustedDeviceRepository.findByDeviceTokenHash(hash)).thenReturn(Optional.of(device));

        Optional<String> result = gate.challengeIfRequired(user, rawDeviceToken);

        assertThat(result).isEmpty();
        verify(emailCodeService, never()).issueCode(any(), any(), any());
    }

    @Test
    void issuesChallengeAndSendsOtpWhenDeviceIsRevokedExpiredOrForeign() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user(userId, true);
        String rawDeviceToken = SecurityCodeCrypto.generateSecureToken();
        String hash = SecurityCodeCrypto.sha256Hex(rawDeviceToken);

        TrustedDevice revoked = TrustedDevice.create(userId, hash, "Old Laptop", NOW.plus(Duration.ofDays(10)));
        revoked.revoke(NOW.minusSeconds(60));
        when(trustedDeviceRepository.findByDeviceTokenHash(hash)).thenReturn(Optional.of(revoked));
        when(requestIpProvider.currentRequestIp()).thenReturn("198.51.100.9");
        when(emailCodeService.issueCode(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, "198.51.100.9"))
                .thenReturn("012345");
        when(accessTokenIssuer.issueChallenge(userId, Duration.ofMinutes(10)))
                .thenReturn("challenge-jwt");

        Optional<String> result = gate.challengeIfRequired(user, rawDeviceToken);

        assertThat(result).contains("challenge-jwt");
        verify(authMailSender).sendTwoFactorCodeEmail(user.email(), user.fullName(), "012345");
    }

    private static UserAccount user(UUID userId, boolean twoFactorEnabled) {
        return new UserAccount(
                userId,
                UUID.randomUUID(),
                "user@familion.test",
                "Test User",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                "{bcrypt}hash",
                true,
                twoFactorEnabled,
                true);
    }
}
