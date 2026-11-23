package com.familion.auth.application;

import com.familion.auth.application.api.SessionIssuer;
import com.familion.auth.domain.EmailCodePurpose;
import com.familion.common.error.BusinessRuleException;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    static final String INVALID_TOKEN_MESSAGE = "Invalid or expired password reset token";

    private final UserAccountApi userAccountApi;
    private final EmailCodeService emailCodeService;
    private final SessionIssuer sessionIssuer;
    private final SecuritySettingsService securitySettingsService;
    private final LoginLockoutService loginLockoutService;
    private final AuthMailSender authMailSender;
    private final String publicUrl;

    public PasswordResetService(
            UserAccountApi userAccountApi,
            EmailCodeService emailCodeService,
            SessionIssuer sessionIssuer,
            SecuritySettingsService securitySettingsService,
            LoginLockoutService loginLockoutService,
            AuthMailSender authMailSender,
            @Value("${familion.app.public-url}") String publicUrl) {
        this.userAccountApi = userAccountApi;
        this.emailCodeService = emailCodeService;
        this.sessionIssuer = sessionIssuer;
        this.securitySettingsService = securitySettingsService;
        this.loginLockoutService = loginLockoutService;
        this.authMailSender = authMailSender;
        this.publicUrl = stripTrailingSlashes(publicUrl);
    }

    public void requestPasswordReset(String email, String requestIp) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> maybeUser = userAccountApi.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty() || !maybeUser.get().active()) {
            return;
        }

        UserAccount user = maybeUser.get();
        String rawToken =
                emailCodeService.issueCode(user.id(), EmailCodePurpose.PASSWORD_RESET, requestIp);
        String resetUrl = publicUrl + "/auth/reset-password?token=" + rawToken;
        authMailSender.sendPasswordResetEmailAsync(user.email(), user.fullName(), resetUrl);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void resetPassword(String rawToken, String newPassword) {
        UUID userId = emailCodeService
                .consumeResetToken(rawToken)
                .orElseThrow(() -> new BusinessRuleException(INVALID_TOKEN_MESSAGE));

        UserAccount user = userAccountApi.require(userId);
        if (!user.active()) {
            throw new BusinessRuleException(INVALID_TOKEN_MESSAGE);
        }

        userAccountApi.changePassword(userId, newPassword);
        sessionIssuer.revokeAllSessions(userId);
        securitySettingsService.revokeAllTrustedDevices(userId);
        loginLockoutService.reset(user.email());
        authMailSender.sendPasswordChangedEmail(user.email(), user.fullName());
    }

    private static String stripTrailingSlashes(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
