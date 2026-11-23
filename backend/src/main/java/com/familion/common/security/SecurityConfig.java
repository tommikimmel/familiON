package com.familion.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(SecurityProperties properties) {
        if (properties.jwtSecret() == null || properties.jwtSecret().isBlank()) {
            throw new IllegalStateException("familion.security.jwt-secret must be configured");
        }
        byte[] decoded = Base64.getDecoder().decode(properties.jwtSecret());
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "familion.security.jwt-secret must decode to at least "
                            + MIN_SECRET_BYTES
                            + " bytes for HS256");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey, TokenRevocationService tokenRevocationService, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);

        OAuth2TokenValidator<Jwt> accessTypeValidator = jwt -> {
            if (JwtAccessTokenIssuer.TOKEN_TYPE_ACCESS.equals(jwt.getClaimAsString("typ"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token type must be access", null));
        };

        OAuth2TokenValidator<Jwt> revocationValidator = jwt -> {
            String jtiClaim = jwt.getId();
            if (jtiClaim == null || jtiClaim.isBlank()) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token is missing jti claim", null));
            }
            UUID jti;
            try {
                jti = UUID.fromString(jtiClaim);
            } catch (IllegalArgumentException ex) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token has invalid jti claim", null));
            }
            if (tokenRevocationService.isRevoked(jti)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token has been revoked", null));
            }
            return OAuth2TokenValidatorResult.success();
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator, accessTypeValidator, revocationValidator));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    public AuthenticationEntryPoint problemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ProblemAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public AccessDeniedHandler problemAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ProblemAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/register-invite",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/login",
                                "/api/v1/auth/2fa/verify",
                                "/api/v1/auth/2fa/resend",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/version")
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
