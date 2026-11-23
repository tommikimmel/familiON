package com.familion.common.security;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/security")
class SecurityTestController {

    private final CurrentUserProvider currentUserProvider;

    SecurityTestController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    CurrentUser me() {
        return currentUserProvider.get();
    }

    @GetMapping("/adult-only")
    @PreAuthorize("hasRole('ADULTO')")
    Map<String, String> adultOnly() {
        return Map.of("status", "ok");
    }
}
