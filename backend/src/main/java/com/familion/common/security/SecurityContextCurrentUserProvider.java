package com.familion.common.security;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user found in security context");
        }

        try {
            UUID userId = UUID.fromString(requireClaim(jwt.getSubject(), "sub"));
            UUID familyId = UUID.fromString(requireClaim(jwt.getClaimAsString("fam"), "fam"));
            Role role = Role.valueOf(requireClaim(jwt.getClaimAsString("role"), "role"));
            FamilyVisibility visibility =
                    FamilyVisibility.valueOf(requireClaim(jwt.getClaimAsString("vis"), "vis"));
            return new CurrentUser(userId, familyId, role, visibility);
        } catch (IllegalArgumentException ex) {
            throw new AuthenticationCredentialsNotFoundException("Invalid JWT user claims", ex);
        }
    }

    private static String requireClaim(String value, String claimName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required JWT claim: " + claimName);
        }
        return value;
    }
}
