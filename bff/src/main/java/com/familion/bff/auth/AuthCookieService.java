package com.familion.bff.auth;

import com.familion.bff.config.BffProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = AccessTokenResolver.ACCESS_TOKEN_COOKIE;
    public static final String REFRESH_TOKEN_COOKIE = "FAMILION_RT";
    public static final String DEVICE_TOKEN_COOKIE = "FAMILION_DT";

    private static final String ACCESS_TOKEN_PATH = "/bff";
    private static final String AUTH_COOKIE_PATH = "/bff/auth";
    private static final long DEFAULT_ACCESS_TOKEN_MAX_AGE = 900L;
    private static final long DEFAULT_REFRESH_TOKEN_MAX_AGE = 2_592_000L;
    private static final long DEVICE_TOKEN_MAX_AGE = 2_592_000L;

    private final BffProperties properties;

    public AuthCookieService(BffProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie accessTokenCookie(String token, Long expiresInSeconds) {
        long maxAge = expiresInSeconds != null && expiresInSeconds > 0
                ? expiresInSeconds
                : DEFAULT_ACCESS_TOKEN_MAX_AGE;
        return buildCookie(ACCESS_TOKEN_COOKIE, token, ACCESS_TOKEN_PATH, Duration.ofSeconds(maxAge));
    }

    public ResponseCookie refreshTokenCookie(String token, Long expiresInSeconds) {
        long maxAge = expiresInSeconds != null && expiresInSeconds > 0
                ? expiresInSeconds
                : DEFAULT_REFRESH_TOKEN_MAX_AGE;
        return buildCookie(REFRESH_TOKEN_COOKIE, token, AUTH_COOKIE_PATH, Duration.ofSeconds(maxAge));
    }

    public ResponseCookie deviceTokenCookie(String token) {
        return buildCookie(DEVICE_TOKEN_COOKIE, token, AUTH_COOKIE_PATH, Duration.ofSeconds(DEVICE_TOKEN_MAX_AGE));
    }

    public ResponseCookie clearAccessTokenCookie() {
        return buildCookie(ACCESS_TOKEN_COOKIE, "", ACCESS_TOKEN_PATH, Duration.ZERO);
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return buildCookie(REFRESH_TOKEN_COOKIE, "", AUTH_COOKIE_PATH, Duration.ZERO);
    }

    public void addSessionCookies(HttpHeaders headers, CoreTokenResponse tokens) {
        if (tokens.accessToken() != null && !tokens.accessToken().isBlank()) {
            headers.add(
                    HttpHeaders.SET_COOKIE,
                    accessTokenCookie(tokens.accessToken(), tokens.accessTokenExpiresIn()).toString());
        }
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            headers.add(
                    HttpHeaders.SET_COOKIE,
                    refreshTokenCookie(tokens.refreshToken(), tokens.refreshTokenExpiresIn()).toString());
        }
        if (tokens.deviceToken() != null && !tokens.deviceToken().isBlank()) {
            headers.add(
                    HttpHeaders.SET_COOKIE,
                    deviceTokenCookie(tokens.deviceToken()).toString());
        }
    }

    public void clearSessionCookies(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, clearAccessTokenCookie().toString());
        headers.add(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie().toString());
    }

    public Optional<String> readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie buildCookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
