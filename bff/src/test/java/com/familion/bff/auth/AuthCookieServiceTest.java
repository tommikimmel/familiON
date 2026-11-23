package com.familion.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.bff.config.BffProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class AuthCookieServiceTest {

    @Test
    void setsSecureAttributeWhenCookieSecureIsTrue() {
        AuthCookieService service = new AuthCookieService(new BffProperties("http://localhost:8081", true, 120));

        ResponseCookie at = service.accessTokenCookie("at-value", 900L);
        ResponseCookie rt = service.refreshTokenCookie("rt-value", 2592000L);
        ResponseCookie dt = service.deviceTokenCookie("dt-value");

        assertThat(at.isHttpOnly()).isTrue();
        assertThat(at.isSecure()).isTrue();
        assertThat(at.getSameSite()).isEqualTo("Strict");
        assertThat(at.getPath()).isEqualTo("/bff");
        assertThat(at.getMaxAge().getSeconds()).isEqualTo(900L);

        assertThat(rt.isHttpOnly()).isTrue();
        assertThat(rt.isSecure()).isTrue();
        assertThat(rt.getSameSite()).isEqualTo("Strict");
        assertThat(rt.getPath()).isEqualTo("/bff/auth");
        assertThat(rt.getMaxAge().getSeconds()).isEqualTo(2592000L);

        assertThat(dt.isHttpOnly()).isTrue();
        assertThat(dt.isSecure()).isTrue();
        assertThat(dt.getSameSite()).isEqualTo("Strict");
        assertThat(dt.getPath()).isEqualTo("/bff/auth");
        assertThat(dt.getMaxAge().getSeconds()).isEqualTo(2592000L);
    }

    @Test
    void omitsSecureAttributeWhenCookieSecureIsFalse() {
        AuthCookieService service = new AuthCookieService(new BffProperties("http://localhost:8081", false, 120));

        assertThat(service.accessTokenCookie("at", 900L).isSecure()).isFalse();
        assertThat(service.clearAccessTokenCookie().getMaxAge().isZero()).isTrue();
        assertThat(service.clearRefreshTokenCookie().getMaxAge().isZero()).isTrue();
    }
}
