package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AuthPasswordResetIT extends IntegrationTestBase {

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

    @Test
    void forgotPasswordReturns202WithoutSendingEmailForUnknownAddress() {
        ResponseEntity<Void> response = client()
                .post()
                .uri("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "nobody-" + UUID.randomUUID() + "@familion.test"))
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void forgotAndResetPasswordUpdatesCredentialsAndRevokesSessionsAndTrustedDevices()
            throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@familion.test";
        ResponseEntity<JsonNode> reg = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "OriginalPass123",
                        "fullName", "Reset User",
                        "familyName", "Familia Reset"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Complete 2FA login with trustDevice=true to obtain both a refresh token and a trusted device
        resetMailMock();
        JsonNode login1 = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "OriginalPass123"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();
        String challengeToken = login1.path("challengeToken").asText();

        ArgumentCaptor<MimeMessage> otpCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(otpCaptor.capture());
        String otp = MimeMessageTestSupport.extractSixDigitCode(otpCaptor.getValue());

        JsonNode session = client()
                .post()
                .uri("/api/v1/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "challengeToken", challengeToken,
                        "code", otp,
                        "trustDevice", true,
                        "deviceName", "Laptop Personal"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();
        String oldRefreshToken = session.path("refreshToken").asText();
        String oldDeviceToken = session.path("deviceToken").asText();

        // Request password reset
        resetMailMock();
        ResponseEntity<Void> forgotResp = client()
                .post()
                .uri("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .retrieve()
                .toBodilessEntity();
        assertThat(forgotResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<MimeMessage> resetMailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, timeout(5000)).send(resetMailCaptor.capture());
        MimeMessage resetMail = resetMailCaptor.getValue();
        assertThat(resetMail.getSubject()).isEqualTo(ThymeleafAuthMailSender.SUBJECT_PASSWORD_RESET);
        String resetUrl = MimeMessageTestSupport.extractResetUrl(resetMail);
        assertThat(resetUrl).startsWith("http://localhost:8088/auth/reset-password?token=");
        String resetToken = MimeMessageTestSupport.extractResetToken(resetMail);

        // Reset token is never stored in plain text
        Integer plainTokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_security_codes WHERE code_hash = ?",
                Integer.class,
                resetToken);
        assertThat(plainTokenCount).isZero();
        Integer hashedTokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_security_codes WHERE code_hash = ?",
                Integer.class,
                SecurityCodeCrypto.sha256Hex(resetToken));
        assertThat(hashedTokenCount).isEqualTo(1);

        // Weak password -> 400
        ResponseEntity<JsonNode> weakPass = client()
                .post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", resetToken, "newPassword", "weak"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(weakPass.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Invalid token -> 422
        ResponseEntity<JsonNode> badToken = client()
                .post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", "nonexistent-token-value", "newPassword", "NewStrongPass9"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(badToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Valid reset -> 204 and sends password-changed notification
        resetMailMock();
        ResponseEntity<Void> resetResp = client()
                .post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", resetToken, "newPassword", "NewStrongPass9"))
                .retrieve()
                .toBodilessEntity();
        assertThat(resetResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ArgumentCaptor<MimeMessage> changedCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(changedCaptor.capture());
        assertThat(changedCaptor.getValue().getSubject())
                .isEqualTo(ThymeleafAuthMailSender.SUBJECT_PASSWORD_CHANGED);

        // Reusing reset token -> 422
        ResponseEntity<JsonNode> reuseToken = client()
                .post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", resetToken, "newPassword", "AnotherPass123"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(reuseToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Previous refresh token is revoked -> 401
        ResponseEntity<JsonNode> oldRefresh = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", oldRefreshToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(oldRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Old password no longer works -> 401
        ResponseEntity<JsonNode> oldPassLogin = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "OriginalPass123"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(oldPassLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // New password works AND previous trusted device was revoked (so 2FA is required again)
        ResponseEntity<JsonNode> newPassLogin = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "NewStrongPass9",
                        "deviceToken", oldDeviceToken))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(newPassLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(newPassLogin.getBody().path("requires2fa").asBoolean()).isTrue();
    }

    @Test
    void expiredResetTokenReturns422() throws Exception {
        String email = "reset-exp-" + UUID.randomUUID() + "@familion.test";
        client().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "OriginalPass123",
                        "fullName", "Expired Reset",
                        "familyName", "Familia Expired"))
                .retrieve()
                .toEntity(JsonNode.class);

        resetMailMock();
        client().post()
                .uri("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .retrieve()
                .toBodilessEntity();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, timeout(5000)).send(captor.capture());
        String token = MimeMessageTestSupport.extractResetToken(captor.getValue());

        jdbcTemplate.update(
                "UPDATE email_security_codes SET expires_at = NOW() - INTERVAL '1 minute' WHERE code_hash = ?",
                SecurityCodeCrypto.sha256Hex(token));

        ResponseEntity<JsonNode> expired = client()
                .post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token, "newPassword", "NewStrongPass9"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private void resetMailMock() {
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }
}
