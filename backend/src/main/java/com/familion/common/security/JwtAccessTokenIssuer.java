package com.familion.common.security;

import com.familion.common.error.BusinessRuleException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    static final String TOKEN_TYPE_ACCESS = "access";
    static final String TOKEN_TYPE_2FA = "2fa";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder challengeDecoder;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtAccessTokenIssuer(SecretKey jwtSecretKey, SecurityProperties properties, Clock clock) {
        Objects.requireNonNull(jwtSecretKey, "jwtSecretKey must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (properties.accessTtl() == null || !properties.accessTtl().isPositive()) {
            throw new IllegalArgumentException("familion.security.access-ttl must be positive");
        }

        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
        this.accessTtl = properties.accessTtl();
        this.clock = clock;

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> challengeTypeValidator = jwt -> {
            if (TOKEN_TYPE_2FA.equals(jwt.getClaimAsString("typ"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid challenge token type", null));
        };
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(timestampValidator, challengeTypeValidator));
        this.challengeDecoder = decoder;
    }

    @Override
    public IssuedAccessToken issue(UUID userId, UUID familyId, Role role, FamilyVisibility visibility) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(visibility, "visibility must not be null");

        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(accessTtl);
        UUID jti = UUID.randomUUID();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .id(jti.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("fam", familyId.toString())
                .claim("role", role.name())
                .claim("vis", visibility.name())
                .claim("typ", TOKEN_TYPE_ACCESS)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(tokenValue, jti, expiresAt, accessTtl.toSeconds());
    }

    @Override
    public String issueChallenge(UUID userId, Duration ttl) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (!ttl.isPositive()) {
            throw new IllegalArgumentException("Challenge TTL must be positive");
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(ttl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("typ", TOKEN_TYPE_2FA)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public UUID parseChallenge(String challengeToken) {
        if (challengeToken == null || challengeToken.isBlank()) {
            throw new BusinessRuleException("Invalid or expired 2FA challenge token");
        }
        try {
            Jwt jwt = challengeDecoder.decode(challengeToken);
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new BusinessRuleException("Invalid or expired 2FA challenge token");
            }
            return UUID.fromString(subject);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid or expired 2FA challenge token", ex);
        }
    }
}
