package com.familion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.auth.application.api.SecondFactorGate;
import com.familion.auth.application.api.SessionIssuer;
import com.familion.auth.application.api.SessionTokens;
import com.familion.auth.domain.AuthUnauthorizedException;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import com.familion.family.application.api.UserProfileApi;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserAccountApi userAccountApi;
    private UserProfileApi userProfileApi;
    private PasswordEncoder passwordEncoder;
    private LoginLockoutService loginLockoutService;
    private SessionIssuer sessionIssuer;
    private ObjectProvider<SecondFactorGate> secondFactorGateProvider;
    private ApplicationEventPublisher eventPublisher;
    private AuthService authService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        this.userAccountApi = mock(UserAccountApi.class);
        this.userProfileApi = mock(UserProfileApi.class);
        this.passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        this.loginLockoutService = mock(LoginLockoutService.class);
        this.sessionIssuer = mock(SessionIssuer.class);
        this.secondFactorGateProvider = mock(ObjectProvider.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.authService = new AuthService(
                userAccountApi,
                userProfileApi,
                passwordEncoder,
                loginLockoutService,
                sessionIssuer,
                secondFactorGateProvider,
                eventPublisher);
    }

    @Test
    void throwsGenericUnauthorizedForUnknownInactiveOrWrongPassword() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String hash = passwordEncoder.encode("CorrectPass1");

        when(userAccountApi.findByEmail("missing@familion.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(
                        new LoginCommand("missing@familion.test", "AnyPass123", null), "UA"))
                .isInstanceOf(AuthUnauthorizedException.class)
                .hasMessage("Invalid credentials");

        UserAccount inactive = new UserAccount(
                userId,
                familyId,
                "inactive@familion.test",
                "Inactive",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                hash,
                true,
                true,
                false);
        when(userAccountApi.findByEmail("inactive@familion.test")).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> authService.login(
                        new LoginCommand("inactive@familion.test", "CorrectPass1", null), "UA"))
                .isInstanceOf(AuthUnauthorizedException.class)
                .hasMessage("Invalid credentials");

        UserAccount active = new UserAccount(
                userId,
                familyId,
                "active@familion.test",
                "Active",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                hash,
                false,
                true,
                true);
        when(userAccountApi.findByEmail("active@familion.test")).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> authService.login(
                        new LoginCommand("active@familion.test", "WrongPass99", null), "UA"))
                .isInstanceOf(AuthUnauthorizedException.class)
                .hasMessage("Invalid credentials");

        verify(loginLockoutService).recordFailure("missing@familion.test");
        verify(loginLockoutService).recordFailure("inactive@familion.test");
        verify(loginLockoutService).recordFailure("active@familion.test");
    }

    @Test
    void returnsChallengeWithoutIssuingSessionWhenSecondFactorGateChallenges() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UserAccount user = new UserAccount(
                userId,
                familyId,
                "user@familion.test",
                "User",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                passwordEncoder.encode("CorrectPass1"),
                true,
                true,
                true);
        when(userAccountApi.findByEmail("user@familion.test")).thenReturn(Optional.of(user));
        SecondFactorGate gate = mock(SecondFactorGate.class);
        when(secondFactorGateProvider.getIfAvailable()).thenReturn(gate);
        when(gate.challengeIfRequired(user, "dev-tok")).thenReturn(Optional.of("challenge-jwt"));

        SessionTokens result =
                authService.login(new LoginCommand("user@familion.test", "CorrectPass1", "dev-tok"), "UA");

        assertThat(result.requires2fa()).isTrue();
        assertThat(result.challengeToken()).isEqualTo("challenge-jwt");
        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        verify(loginLockoutService).reset("user@familion.test");
        verify(sessionIssuer, never()).issueSession(any(), anyString());
    }
}
