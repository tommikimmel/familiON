package com.familion.auth.application;

import com.familion.auth.application.api.SecondFactorGate;
import com.familion.auth.application.api.SessionIssuer;
import com.familion.auth.application.api.SessionTokens;
import com.familion.auth.application.api.UserRegisteredEvent;
import com.familion.auth.domain.AuthUnauthorizedException;
import com.familion.family.application.api.RegisteredUser;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import com.familion.family.application.api.UserProfileApi;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    private final UserAccountApi userAccountApi;
    private final UserProfileApi userProfileApi;
    private final PasswordEncoder passwordEncoder;
    private final LoginLockoutService loginLockoutService;
    private final SessionIssuer sessionIssuer;
    private final ObjectProvider<SecondFactorGate> secondFactorGateProvider;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(
            UserAccountApi userAccountApi,
            UserProfileApi userProfileApi,
            PasswordEncoder passwordEncoder,
            LoginLockoutService loginLockoutService,
            SessionIssuer sessionIssuer,
            ObjectProvider<SecondFactorGate> secondFactorGateProvider,
            ApplicationEventPublisher eventPublisher) {
        this.userAccountApi = userAccountApi;
        this.userProfileApi = userProfileApi;
        this.passwordEncoder = passwordEncoder;
        this.loginLockoutService = loginLockoutService;
        this.sessionIssuer = sessionIssuer;
        this.secondFactorGateProvider = secondFactorGateProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RegisteredUser register(RegisterCommand command) {
        RegisteredUser registered = userProfileApi.registerFamilyWithAdult(
                command.email(), command.password(), command.fullName(), command.familyName());
        eventPublisher.publishEvent(new UserRegisteredEvent(
                registered.userId(),
                registered.familyId(),
                registered.email(),
                registered.fullName()));
        return registered;
    }

    public SessionTokens login(LoginCommand command, String deviceInfo) {
        String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
        loginLockoutService.assertNotLocked(normalizedEmail);

        Optional<UserAccount> maybeUser = userAccountApi.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()
                || !maybeUser.get().active()
                || !passwordMatches(command.password(), maybeUser.get().passwordHash())) {
            loginLockoutService.recordFailure(normalizedEmail);
            throw new AuthUnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        loginLockoutService.reset(normalizedEmail);
        UserAccount user = maybeUser.get();

        SecondFactorGate gate = secondFactorGateProvider.getIfAvailable();
        if (gate != null) {
            Optional<String> challengeToken = gate.challengeIfRequired(user, command.deviceToken());
            if (challengeToken.isPresent()) {
                return new SessionTokens(true, challengeToken.get(), null, null, null, null, null);
            }
        }

        return sessionIssuer.issueSession(user.id(), deviceInfo);
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
