package com.familion.auth.application;

import com.familion.auth.application.api.SecondFactorGate;
import com.familion.auth.domain.EmailCodePurpose;
import com.familion.auth.domain.SecurityCodeCrypto;
import com.familion.auth.domain.TrustedDevice;
import com.familion.auth.infrastructure.persistence.TrustedDeviceRepository;
import com.familion.common.security.AccessTokenIssuer;
import com.familion.family.application.api.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SecondFactorGateImpl implements SecondFactorGate {

    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);

    private final TrustedDeviceRepository trustedDeviceRepository;
    private final EmailCodeService emailCodeService;
    private final AuthMailSender authMailSender;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RequestIpProvider requestIpProvider;
    private final Clock clock;

    public SecondFactorGateImpl(
            TrustedDeviceRepository trustedDeviceRepository,
            EmailCodeService emailCodeService,
            AuthMailSender authMailSender,
            AccessTokenIssuer accessTokenIssuer,
            RequestIpProvider requestIpProvider,
            Clock clock) {
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.emailCodeService = emailCodeService;
        this.authMailSender = authMailSender;
        this.accessTokenIssuer = accessTokenIssuer;
        this.requestIpProvider = requestIpProvider;
        this.clock = clock;
    }

    @Override
    public Optional<String> challengeIfRequired(UserAccount user, String deviceToken) {
        Objects.requireNonNull(user, "user must not be null");
        if (!user.twoFactorEnabled()) {
            return Optional.empty();
        }
        if (isTrustedDevice(user, deviceToken)) {
            return Optional.empty();
        }

        String otp = emailCodeService.issueCode(
                user.id(), EmailCodePurpose.TWO_FACTOR_LOGIN, requestIpProvider.currentRequestIp());
        authMailSender.sendTwoFactorCodeEmail(user.email(), user.fullName(), otp);
        return Optional.of(accessTokenIssuer.issueChallenge(user.id(), CHALLENGE_TTL));
    }

    private boolean isTrustedDevice(UserAccount user, String rawDeviceToken) {
        if (rawDeviceToken == null || rawDeviceToken.isBlank()) {
            return false;
        }
        String candidateHash = SecurityCodeCrypto.sha256Hex(rawDeviceToken.trim());
        Optional<TrustedDevice> maybeDevice =
                trustedDeviceRepository.findByDeviceTokenHash(candidateHash);
        if (maybeDevice.isEmpty()) {
            return false;
        }
        TrustedDevice device = maybeDevice.get();
        Instant now = clock.instant();
        return device.getUserId().equals(user.id())
                && SecurityCodeCrypto.constantTimeEquals(device.getDeviceTokenHash(), candidateHash)
                && device.isActive(now);
    }
}
