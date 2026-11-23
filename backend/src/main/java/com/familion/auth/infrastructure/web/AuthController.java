package com.familion.auth.infrastructure.web;

import com.familion.auth.application.AuthService;
import com.familion.auth.application.LoginCommand;
import com.familion.auth.application.RegisterCommand;
import com.familion.auth.application.api.SessionTokens;
import com.familion.family.application.api.RegisteredUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        RegisteredUser registered = authService.register(new RegisterCommand(
                request.email(), request.password(), request.fullName(), request.familyName()));
        return new RegisterResponse(registered.userId(), registered.email());
    }

    @PostMapping("/login")
    public SessionTokens login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.login(
                new LoginCommand(request.email(), request.password(), request.deviceToken()),
                userAgent);
    }
}
