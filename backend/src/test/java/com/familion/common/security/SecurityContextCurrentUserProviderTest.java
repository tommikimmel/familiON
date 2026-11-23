package com.familion.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityContextCurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider =
            new SecurityContextCurrentUserProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void throwsAuthenticationCredentialsNotFoundWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(provider::get)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void extractsCurrentUserFromJwtAuthentication() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("fam", familyId.toString())
                .claim("role", "HIJO")
                .claim("vis", "FAMILY_SUMMARY")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_HIJO"))));

        CurrentUser currentUser = provider.get();

        assertThat(currentUser)
                .isEqualTo(new CurrentUser(
                        userId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY));
    }
}
