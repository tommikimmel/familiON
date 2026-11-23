package com.familion.auth.application;

import com.familion.auth.application.api.SessionIssuer;
import com.familion.auth.application.api.SessionTokens;
import com.familion.auth.domain.AuthUnauthorizedException;
import com.familion.auth.domain.EmailCodePurpose;
import com.familion.auth.domain.SecurityCodeCrypto;
import com.familion.auth.domain.TrustedDevice;
import com.familion.auth.infrastructure.persistence.TrustedDeviceRepository;
import com.familion.common.error.BusinessRuleException;
import com.familion.common.security.AccessTokenIssuer;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TwoFactorService {

    static final Duration TRUSTED_DEVICE_TTL = Duration.ofDays(30);
    static final String INVALID_CHALLENGE_MESSAGE = "Invalid or expired 2FA challenge token";

    private final AccessTokenIssuer accessTokenIssuer;
    private final UserAccountApi userAccountApi;
    private final EmailCodeService emailCodeService;
    private final OtpCooldownService otpCooldownService;
    private final AuthMailSender authMailSender;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final SessionIssuer sessionIssuer;
    private final Clock clock;

    public TwoFactorService(
            AccessTokenIssuer accessTokenIssuer,
            UserAccountApi userAccountApi,
            EmailCodeService emailCodeService,
            OtpCooldownService otpCooldownService,
            AuthMailSender authMailSender,
            TrustedDeviceRepository trustedDeviceRepository,
            SessionIssuer sessionIssuer,
            Clock clock) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.userAccountApi = userAccountApi;
        this.emailCodeService = emailCodeService;
        this.otpCooldownService = otpCooldownService;
        this.authMailSender = authMailSender;
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.sessionIssuer = sessionIssuer;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public SessionTokens verify(
            String challengeToken,
            String code,
            boolean trustDevice,
            String deviceName,
            String userAgent) {
        UUID userId = parseChallengeOrThrowUnauthorized(challengeToken);
        UserAccount user = userAccountApi.require(userId);
        if (!user.active()) {
            throw new AuthUnauthorizedException(INVALID_CHALLENGE_MESSAGE);
        }

        CodeVerificationResult verification =
                emailCodeService.verifyCode(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, code);
        if (!verification.valid()) {
            throw new BusinessRuleException(
                    "Invalid or expired verification code. Remaining attempts: "
                            + verification.remainingAttempts());
        }

        String rawDeviceToken = null;
        if (trustDevice) {
            rawDeviceToken = SecurityCodeCrypto.generateSecureToken();
            String tokenHash = SecurityCodeCrypto.sha256Hex(rawDeviceToken);
            String effectiveName =
                    (deviceName != null && !deviceName.isBlank()) ? deviceName : userAgent;
            Instant expiresAt = clock.instant().plus(TRUSTED_DEVICE_TTL);
            trustedDeviceRepository.save(
                    TrustedDevice.create(userId, tokenHash, effectiveName, expiresAt));
        }

        SessionTokens session = sessionIssuer.issueSession(userId, userAgent);
        return new SessionTokens(
                false,
                null,
                session.accessToken(),
                session.accessTokenExpiresIn(),
                session.refreshToken(),
                session.refreshTokenExpiresIn(),
                rawDeviceToken);
    }

    public void resend(String challengeToken, String requestIp) {
        UUID userId = parseChallengeOrThrowUnauthorized(challengeToken);
        UserAccount user = userAccountApi.require(userId);
        if (!user.active()) {
            throw new AuthUnauthorizedException(INVALID_CHALLENGE_MESSAGE);
        }

        otpCooldownService.acquireOrThrow(userId, EmailCodePurpose.TWO_FACTOR_LOGIN);
        String otp =
                emailCodeService.issueCode(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, requestIp);
        authMailSender.sendTwoFactorCodeEmail(user.email(), user.fullName(), otp);
    }

    private UUID parseChallengeOrThrowUnauthorized(String challengeToken) {
        try {
            return accessTokenIssuer.parseChallenge(challengeToken);
        } catch (BusinessRuleException ex) {
            throw new AuthUnauthorizedException(INVALID_CHALLENGE_MESSAGE);
        }
    }
}
