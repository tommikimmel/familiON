package com.familion.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtAccessTokenIssuerTest {

    private static final String BASE64_SECRET =
            "ZmFtaWxpb24tZGV2LW9ubHktand0LXNlY3JldC1rZXktbm90LWZvci1wcm9kLTQ4";
    private static final Instant FIXED_NOW = Instant.parse("2026-05-14T15:30:00Z");

    private static SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(BASE64_SECRET), "HmacSHA256");
    }

    private static SecurityProperties properties() {
        return new SecurityProperties(BASE64_SECRET, Duration.ofMinutes(15), Duration.ofDays(30));
    }

    @Test
    void issuesAndDecodesAccessTokenWithExpectedClaimsAndExpiration() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        SecretKey key = secretKey();
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(key, properties(), clock);

        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        IssuedAccessToken issued =
                issuer.issue(userId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.jti()).isNotNull();
        assertThat(issued.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofMinutes(15)));
        assertThat(issued.expiresInSeconds()).isEqualTo(900L);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());

        Jwt decoded = decoder.decode(issued.token());
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("fam")).isEqualTo(familyId.toString());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADULTO");
        assertThat(decoded.getClaimAsString("vis")).isEqualTo("FAMILY_FULL");
        assertThat(decoded.getId()).isEqualTo(issued.jti().toString());
        assertThat(decoded.getClaimAsString("typ")).isEqualTo("access");
        assertThat(decoded.getIssuedAt()).isEqualTo(FIXED_NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void issuesAndParsesTwoFactorChallengeToken() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(secretKey(), properties(), clock);
        UUID userId = UUID.randomUUID();

        String challenge = issuer.issueChallenge(userId, Duration.ofMinutes(5));

        assertThat(issuer.parseChallenge(challenge)).isEqualTo(userId);
    }

    @Test
    void rejectsAccessTokenWhenParsedAsChallenge() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(secretKey(), properties(), clock);

        IssuedAccessToken accessToken = issuer.issue(
                UUID.randomUUID(), UUID.randomUUID(), Role.HIJO, FamilyVisibility.OWN_ONLY);

        assertThatThrownBy(() -> issuer.parseChallenge(accessToken.token()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsExpiredChallengeToken() {
        MutableClock clock = new MutableClock(FIXED_NOW);
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(secretKey(), properties(), clock);
        UUID userId = UUID.randomUUID();

        String challenge = issuer.issueChallenge(userId, Duration.ofMinutes(5));
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThatThrownBy(() -> issuer.parseChallenge(challenge))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsMalformedOrTamperedChallengeToken() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(secretKey(), properties(), clock);

        assertThatThrownBy(() -> issuer.parseChallenge("not-a-jwt"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> issuer.parseChallenge(""))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> issuer.parseChallenge(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
