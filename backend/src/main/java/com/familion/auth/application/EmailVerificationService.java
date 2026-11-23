package com.familion.auth.application;

import com.familion.auth.application.api.UserRegisteredEvent;
import com.familion.auth.domain.EmailCodePurpose;
import com.familion.common.error.BusinessRuleException;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EmailVerificationService {

    static final String INVALID_CODE_MESSAGE = "Invalid or expired verification code";

    private final UserAccountApi userAccountApi;
    private final EmailCodeService emailCodeService;
    private final OtpCooldownService otpCooldownService;
    private final AuthMailSender authMailSender;
    private final RequestIpProvider requestIpProvider;

    public EmailVerificationService(
            UserAccountApi userAccountApi,
            EmailCodeService emailCodeService,
            OtpCooldownService otpCooldownService,
            AuthMailSender authMailSender,
            RequestIpProvider requestIpProvider) {
        this.userAccountApi = userAccountApi;
        this.emailCodeService = emailCodeService;
        this.otpCooldownService = otpCooldownService;
        this.authMailSender = authMailSender;
        this.requestIpProvider = requestIpProvider;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        String code = emailCodeService.issueCode(
                event.userId(),
                EmailCodePurpose.EMAIL_VERIFICATION,
                requestIpProvider.currentRequestIp());
        authMailSender.sendVerificationEmail(event.email(), event.fullName(), code);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void verifyEmail(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new BusinessRuleException(INVALID_CODE_MESSAGE);
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> maybeUser = userAccountApi.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            throw new BusinessRuleException(INVALID_CODE_MESSAGE);
        }

        UserAccount user = maybeUser.get();
        CodeVerificationResult result =
                emailCodeService.verifyCode(user.id(), EmailCodePurpose.EMAIL_VERIFICATION, code);
        if (!result.valid()) {
            throw new BusinessRuleException(INVALID_CODE_MESSAGE);
        }

        userAccountApi.markEmailVerified(user.id());
    }

    public void resendVerification(UUID userId, String requestIp) {
        Objects.requireNonNull(userId, "userId must not be null");
        UserAccount user = userAccountApi.require(userId);
        if (user.emailVerified()) {
            return;
        }
        otpCooldownService.acquireOrThrow(userId, EmailCodePurpose.EMAIL_VERIFICATION);
        String code = emailCodeService.issueCode(
                userId, EmailCodePurpose.EMAIL_VERIFICATION, requestIp);
        authMailSender.sendVerificationEmail(user.email(), user.fullName(), code);
    }
}
