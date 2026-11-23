package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.auth.domain.SecurityCodeCrypto;
import com.familion.auth.infrastructure.external.ThymeleafAuthMailSender;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

class AuthTwoFactorIT extends IntegrationTestBase {

    @MockitoBean
    private JavaMailSender javaMailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMailMock() {
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private RestClient patchCapableClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void fullTwoFactorFlowWithTrustedDeviceAndSecuritySettings() throws Exception {
        String email = "2fa-flow-" + UUID.randomUUID() + "@familion.test";
        registerUser(email, "TwoFactorPass1");
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));

        // Login with 2FA enabled -> requires2fa = true and sends OTP mail
        ResponseEntity<JsonNode> login1 = client()
                .post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 TestBrowser")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TwoFactorPass1"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(login1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login1.getBody().path("requires2fa").asBoolean()).isTrue();
        String challengeToken = login1.getBody().path("challengeToken").asText();
        assertThat(challengeToken).isNotBlank();
        assertThat(login1.getBody().path("accessToken").isNull()).isTrue();

        ArgumentCaptor<MimeMessage> otpCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(otpCaptor.capture());
        MimeMessage otpMail = otpCaptor.getValue();
        assertThat(otpMail.getSubject()).isEqualTo(ThymeleafAuthMailSender.SUBJECT_TWO_FACTOR);
        String otpCode = MimeMessageTestSupport.extractSixDigitCode(otpMail);

        // Invalid challenge -> 401
        ResponseEntity<JsonNode> badChallenge = client()
                .post()
                .uri("/api/v1/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("challengeToken", "not-a-valid-jwt", "code", otpCode, "trustDevice", false))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(badChallenge.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Wrong OTP -> 422 with remaining attempts in detail
        String wrongCode = otpCode.equals("000000") ? "111111" : "000000";
        ResponseEntity<JsonNode> wrongOtp = client()
                .post()
                .uri("/api/v1/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("challengeToken", challengeToken, "code", wrongCode, "trustDevice", false))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(wrongOtp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(wrongOtp.getBody().path("detail").asText()).contains("4");

        // Correct OTP with trustDevice=true -> 200 SessionTokens including deviceToken
        ResponseEntity<JsonNode> verified = client()
                .post()
                .uri("/api/v1/auth/2fa/verify")
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 TestBrowser")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "challengeToken", challengeToken,
                        "code", otpCode,
                        "trustDevice", true,
                        "deviceName", "Chrome en MacBook"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode verifiedBody = verified.getBody();
        assertThat(verifiedBody.path("requires2fa").asBoolean()).isFalse();
        String accessToken = verifiedBody.path("accessToken").asText();
        String deviceToken = verifiedBody.path("deviceToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(verifiedBody.path("refreshToken").asText()).isNotBlank();
        assertThat(deviceToken).isNotBlank();

        // Device token is stored only as SHA-256 hash
        Integer plainDeviceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trusted_devices WHERE device_token_hash = ?",
                Integer.class,
                deviceToken);
        assertThat(plainDeviceCount).isZero();
        Integer hashedDeviceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trusted_devices WHERE device_token_hash = ?",
                Integer.class,
                SecurityCodeCrypto.sha256Hex(deviceToken));
        assertThat(hashedDeviceCount).isEqualTo(1);

        // Next login with deviceToken skips 2FA
        ResponseEntity<JsonNode> loginWithTrusted = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "TwoFactorPass1",
                        "deviceToken", deviceToken))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(loginWithTrusted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginWithTrusted.getBody().path("requires2fa").asBoolean()).isFalse();
        assertThat(loginWithTrusted.getBody().path("accessToken").asText()).isNotBlank();

        // GET /api/v1/auth/me/security lists the trusted device
        ResponseEntity<JsonNode> securityGet = client()
                .get()
                .uri("/api/v1/auth/me/security")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(securityGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(securityGet.getBody().path("twoFactorEnabled").asBoolean()).isTrue();
        assertThat(securityGet.getBody().path("trustedDevices")).hasSize(1);
        String deviceId = securityGet.getBody().path("trustedDevices").get(0).path("id").asText();
        assertThat(securityGet.getBody().path("trustedDevices").get(0).path("deviceName").asText())
                .isEqualTo("Chrome en MacBook");

        // PATCH /api/v1/auth/me/security disabling 2FA does NOT revoke trusted devices
        RestClient patchClient = patchCapableClient();
        ResponseEntity<JsonNode> disabled = patchClient
                .patch()
                .uri("/api/v1/auth/me/security")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("twoFactorEnabled", false))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody().path("twoFactorEnabled").asBoolean()).isFalse();
        assertThat(disabled.getBody().path("trustedDevices")).hasSize(1);

        // Re-enable 2FA
        ResponseEntity<JsonNode> reEnabled = patchClient
                .patch()
                .uri("/api/v1/auth/me/security")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("twoFactorEnabled", true))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(reEnabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reEnabled.getBody().path("twoFactorEnabled").asBoolean()).isTrue();

        // Another user cannot revoke this user's trusted device -> 404
        String otherEmail = "other-" + UUID.randomUUID() + "@familion.test";
        UUID otherUserId = registerUser(otherEmail, "OtherPass123");
        jdbcTemplate.update("UPDATE users SET two_factor_enabled = false WHERE id = ?", otherUserId);
        String otherToken = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", otherEmail, "password", "OtherPass123"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody()
                .path("accessToken")
                .asText();

        ResponseEntity<JsonNode> foreignDelete = client()
                .delete()
                .uri("/api/v1/auth/me/security/trusted-devices/{id}", deviceId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(foreignDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Owner revokes trusted device -> 204
        ResponseEntity<Void> ownDelete = client()
                .delete()
                .uri("/api/v1/auth/me/security/trusted-devices/{id}", deviceId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
        assertThat(ownDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Login with the revoked deviceToken now requires 2FA again
        ResponseEntity<JsonNode> loginAfterRevoke = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "TwoFactorPass1",
                        "deviceToken", deviceToken))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(loginAfterRevoke.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginAfterRevoke.getBody().path("requires2fa").asBoolean()).isTrue();
    }

    @Test
    void twoFactorResendTwiceReturns429WithRetryAfter() throws Exception {
        String email = "2fa-resend-" + UUID.randomUUID() + "@familion.test";
        registerUser(email, "ResendPass123");
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));

        ResponseEntity<JsonNode> login = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "ResendPass123"))
                .retrieve()
                .toEntity(JsonNode.class);
        String challengeToken = login.getBody().path("challengeToken").asText();

        // First resend -> 202
        ResponseEntity<Void> resend1 = client()
                .post()
                .uri("/api/v1/auth/2fa/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("challengeToken", challengeToken))
                .retrieve()
                .toBodilessEntity();
        assertThat(resend1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Second resend immediately -> 429 + Retry-After
        ResponseEntity<JsonNode> resend2 = client()
                .post()
                .uri("/api/v1/auth/2fa/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("challengeToken", challengeToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(resend2.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        String retryAfter = resend2.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotBlank();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);

        // New resent code works
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeastOnce()).send(captor.capture());
        String latestCode = MimeMessageTestSupport.extractSixDigitCode(
                captor.getAllValues().get(captor.getAllValues().size() - 1));

        ResponseEntity<JsonNode> verifyRes = client()
                .post()
                .uri("/api/v1/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("challengeToken", challengeToken, "code", latestCode, "trustDevice", false))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(verifyRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyRes.getBody().path("deviceToken").isNull()).isTrue();
    }

    private UUID registerUser(String email, String password) {
        ResponseEntity<JsonNode> reg = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password,
                        "fullName", "Two Factor User",
                        "familyName", "Familia 2FA"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(reg.getBody().path("userId").asText());
    }
}
