package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.auth.application.api.SessionIssuer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AuthEmailVerificationIT extends IntegrationTestBase {

    @MockitoBean
    private JavaMailSender javaMailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionIssuer sessionIssuer;

    @BeforeEach
    void setUpMailMock() {
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void registerSendsVerificationEmailAndValidCodeMarksEmailVerified() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@familion.test";
        ResponseEntity<JsonNode> reg = client()
                .post()
                .uri("/api/v1/auth/register")
                .header("X-Forwarded-For", "198.51.100.42, 10.0.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "VerifyPass123",
                        "fullName", "Laura Méndez",
                        "familyName", "Familia Méndez"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID userId = UUID.fromString(reg.getBody().path("userId").asText());

        ArgumentCaptor<MimeMessage> mailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(mailCaptor.capture());
        MimeMessage sent = mailCaptor.getValue();
        assertThat(sent.getSubject()).isEqualTo(ThymeleafAuthMailSender.SUBJECT_VERIFY_EMAIL);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(MimeMessageTestSupport.extractHtmlBody(sent)).contains("#0F766E");

        String code = MimeMessageTestSupport.extractSixDigitCode(sent);

        // Code is never persisted in plain text; request_ip stores first X-Forwarded-For value
        Integer plainCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_security_codes WHERE code_hash = ?",
                Integer.class,
                code);
        assertThat(plainCount).isZero();
        String storedIp = jdbcTemplate.queryForObject(
                "SELECT request_ip FROM email_security_codes WHERE user_id = ? AND purpose = 'EMAIL_VERIFICATION'",
                String.class,
                userId);
        assertThat(storedIp).isEqualTo("198.51.100.42");

        ResponseEntity<Void> verified = client()
                .post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "code", code))
                .retrieve()
                .toBodilessEntity();
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Object verifiedAt = jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE id = ?", Object.class, userId);
        assertThat(verifiedAt).isNotNull();

        // Reusing already consumed code fails with 422
        ResponseEntity<JsonNode> reuse = client()
                .post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "code", code))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void fiveWrongVerificationCodesExhaustAttemptsEvenWhenSixthIsCorrect() throws Exception {
        String email = "maxatt-" + UUID.randomUUID() + "@familion.test";
        ResponseEntity<JsonNode> reg = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "VerifyPass123",
                        "fullName", "Max Attempts",
                        "familyName", "Familia Attempts"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<MimeMessage> mailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(mailCaptor.capture());
        String realCode = MimeMessageTestSupport.extractSixDigitCode(mailCaptor.getValue());
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            ResponseEntity<JsonNode> wrong = client()
                    .post()
                    .uri("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "code", wrongCode))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .toEntity(JsonNode.class);
            assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 6th attempt with the real code must still fail with 422
        ResponseEntity<JsonNode> sixth = client()
                .post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "code", realCode))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void verifyEmailWithUnknownEmailReturnsGeneric422() {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "unknown-" + UUID.randomUUID() + "@familion.test", "code", "123456"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void resendVerificationRequiresAuthConsumesPreviousCodeAndEnforcesCooldown() throws Exception {
        // Without auth -> 401
        ResponseEntity<JsonNode> unauth = client()
                .post()
                .uri("/api/v1/auth/verify-email/resend")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String email = "resend-ver-" + UUID.randomUUID() + "@familion.test";
        ResponseEntity<JsonNode> reg = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "VerifyPass123",
                        "fullName", "Resend User",
                        "familyName", "Familia Resend"))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID userId = UUID.fromString(reg.getBody().path("userId").asText());

        ArgumentCaptor<MimeMessage> firstMail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(firstMail.capture());
        String initialCode = MimeMessageTestSupport.extractSixDigitCode(firstMail.getValue());

        String accessToken = sessionIssuer.issueSession(userId, "test").accessToken();

        // First resend -> 202
        ResponseEntity<Void> resend1 = client()
                .post()
                .uri("/api/v1/auth/verify-email/resend")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
        assertThat(resend1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Second resend immediately -> 429 + Retry-After
        ResponseEntity<JsonNode> resend2 = client()
                .post()
                .uri("/api/v1/auth/verify-email/resend")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(resend2.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resend2.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();

        // Previous code is now consumed
        Object oldConsumedAt = jdbcTemplate.queryForObject(
                "SELECT consumed_at FROM email_security_codes WHERE user_id = ? AND code_hash = ?",
                Object.class,
                userId,
                SecurityCodeCrypto.sha256Hex(initialCode));
        assertThat(oldConsumedAt).isNotNull();

        ArgumentCaptor<MimeMessage> allMails = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeastOnce()).send(allMails.capture());
        String newCode = MimeMessageTestSupport.extractSixDigitCode(
                allMails.getAllValues().get(allMails.getAllValues().size() - 1));

        // New code succeeds
        ResponseEntity<Void> verifyNew = client()
                .post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "code", newCode))
                .retrieve()
                .toBodilessEntity();
        assertThat(verifyNew.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
