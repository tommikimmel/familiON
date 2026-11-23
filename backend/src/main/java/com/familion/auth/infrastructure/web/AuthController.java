package com.familion.auth.infrastructure.web;

import com.familion.auth.application.AuthService;
import com.familion.auth.application.EmailVerificationService;
import com.familion.auth.application.LoginCommand;
import com.familion.auth.application.RegisterCommand;
import com.familion.auth.application.SessionService;
import com.familion.auth.application.api.SessionTokens;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.CurrentUserProvider;
import com.familion.family.application.api.RegisteredUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final EmailVerificationService emailVerificationService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(
            AuthService authService,
            SessionService sessionService,
            EmailVerificationService emailVerificationService,
            CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.emailVerificationService = emailVerificationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        RegisteredUser registered = authService.register(new RegisterCommand(
                request.email(), request.password(), request.fullName(), request.familyName()));
        return new RegisterResponse(registered.userId(), registered.email());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.email(), request.code());
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(HttpServletRequest httpRequest) {
        CurrentUser currentUser = currentUserProvider.get();
        emailVerificationService.resendVerification(
                currentUser.userId(), ServletRequestIpProvider.extractClientIp(httpRequest));
    }

    @PostMapping("/login")
    public SessionTokens login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.login(
                new LoginCommand(request.email(), request.password(), request.deviceToken()),
                userAgent);
    }

    @PostMapping("/refresh")
    public SessionTokens refresh(
            @Valid @RequestBody RefreshRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return sessionService.refresh(request.refreshToken(), userAgent);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestBody(required = false) LogoutRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser currentUser = currentUserProvider.get();
        UUID jti = (jwt != null && jwt.getId() != null) ? UUID.fromString(jwt.getId()) : null;
        Instant expiresAt = jwt != null ? jwt.getExpiresAt() : null;
        String refreshToken = request != null ? request.refreshToken() : null;
        sessionService.logout(currentUser.userId(), jti, expiresAt, refreshToken);
    }
}
