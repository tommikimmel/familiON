package com.familion.bff.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.bff.auth.AccessTokenResolver;
import com.familion.bff.config.BffProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("127.0.0.1");
        redisProperties.setPort(1);
        filter = new RateLimitFilter(
                new BffProperties("http://localhost:8081", false, 120),
                redisProperties,
                new AccessTokenResolver(),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        filter.shutdown();
    }

    @Test
    void resolvesUserKeyFromJwtPayloadWithoutVerifyingSignature() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"usr-999\",\"role\":\"ADULTO\"}".getBytes(StandardCharsets.UTF_8));
        String jwt = header + "." + payload + ".invalid-signature-ignored";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bff/api/accounts");
        request.setCookies(new Cookie("FAMILION_AT", jwt));
        request.setRemoteAddr("10.0.0.5");

        assertThat(filter.resolveRateLimitKey(request)).isEqualTo("rl:bff:user:usr-999");
    }

    @Test
    void fallsBackToFirstForwardedIpOrRemoteAddrWhenTokenAbsentOrMalformed() {
        MockHttpServletRequest withXff = new MockHttpServletRequest("GET", "/bff/api/accounts");
        withXff.setCookies(new Cookie("FAMILION_AT", "not-a-valid-jwt"));
        withXff.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        withXff.setRemoteAddr("127.0.0.1");

        assertThat(filter.resolveRateLimitKey(withXff)).isEqualTo("rl:bff:ip:203.0.113.7");

        MockHttpServletRequest withRemoteAddr = new MockHttpServletRequest("GET", "/bff/api/accounts");
        withRemoteAddr.setRemoteAddr("192.0.2.55");

        assertThat(filter.resolveRateLimitKey(withRemoteAddr)).isEqualTo("rl:bff:ip:192.0.2.55");
    }

    @Test
    void skipsActuatorEndpointsOnly() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/info"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/bff/api/accounts"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/bff/csrf"))).isFalse();
    }
}
