package com.familion.family.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.family.domain.InvitationTokenCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class FamilyInvitationsIT extends IntegrationTestBase {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMailMock() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    void createInvitationPersistsHashOnlySendsEmailAndReturnsInviteUrlWithToken() throws Exception {
        UUID familyId = fixtures.createFamily("Familia Quiroga");
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-inv-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String inviteeEmail = "child-invite-" + UUID.randomUUID() + "@familion.test";

        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", inviteeEmail.toUpperCase(),
                        "role", "HIJO",
                        "familyVisibility", "FAMILY_SUMMARY"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = created.getBody();
        assertThat(body).isNotNull();
        UUID invitationId = UUID.fromString(body.path("id").asText());
        assertThat(body.path("email").asText()).isEqualTo(inviteeEmail);
        assertThat(body.path("role").asText()).isEqualTo("HIJO");
        assertThat(body.path("familyVisibility").asText()).isEqualTo("FAMILY_SUMMARY");
        assertThat(body.path("expiresAt").asText()).isNotBlank();
        assertThat(body.path("createdAt").asText()).isNotBlank();
        assertThat(body.path("invitedBy").path("id").asText()).isEqualTo(adultId.toString());
        assertThat(body.path("invitedBy").path("fullName").asText()).isNotBlank();

        String inviteUrl = body.path("inviteUrl").asText();
        assertThat(inviteUrl).startsWith("http://localhost:8088/auth/invite?token=");
        String rawToken = inviteUrl.substring(inviteUrl.indexOf("token=") + "token=".length());
        assertThat(rawToken).hasSize(43);

        // Database stores SHA-256 hash, never plaintext token
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM family_invitations WHERE id = ?",
                String.class,
                invitationId);
        assertThat(storedHash).isEqualTo(InvitationTokenCrypto.sha256Hex(rawToken));
        assertThat(storedHash).isNotEqualTo(rawToken);

        // Email sent via JavaMailSender
        ArgumentCaptor<MimeMessage> mailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        MimeMessage sent = mailCaptor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Te invitaron a FamiliON");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo(inviteeEmail);
        String emailText = extractText(sent.getContent());
        assertThat(emailText).contains("Familia Quiroga");
        assertThat(emailText).contains(inviteUrl);
    }

    @Test
    void mailFailureStillCreatesInvitationAndReturnsInviteUrl() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender)
                .send(any(MimeMessage.class));

        UUID familyId = fixtures.createFamily("Familia MailFail");
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-mailfail-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "fallback-" + UUID.randomUUID() + "@familion.test",
                        "role", "ADULTO",
                        "familyVisibility", "OWN_ONLY"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        // ADULTO forces FAMILY_FULL regardless of requested visibility
        assertThat(created.getBody().path("familyVisibility").asText()).isEqualTo("FAMILY_FULL");
        assertThat(created.getBody().path("inviteUrl").asText())
                .startsWith("http://localhost:8088/auth/invite?token=");
    }

    @Test
    void enforcesRolePermissionsDuplicateEmailConflictListFilteringAndDelete() {
        UUID familyA = fixtures.createFamily("Family Inv A");
        UUID familyB = fixtures.createFamily("Family Inv B");
        String existingEmail = "existing-" + UUID.randomUUID() + "@familion.test";
        UUID adultA = fixtures.createUser(familyA, existingEmail, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        UUID childA = fixtures.createUser(
                familyA,
                "child-inv-perm-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID adultB = fixtures.createUser(
                familyB,
                "adult-b-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);

        String adultABearer =
                tokens.bearer(adultA, familyA, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childABearer =
                tokens.bearer(childA, familyA, Role.HIJO, FamilyVisibility.OWN_ONLY);
        String adultBBearer =
                tokens.bearer(adultB, familyB, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        // Child cannot list or create invitations -> 403
        ResponseEntity<JsonNode> childList = client()
                .get()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, childABearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> childCreate = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, childABearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "new-" + UUID.randomUUID() + "@familion.test", "role", "HIJO"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Existing registered email -> 409
        ResponseEntity<JsonNode> conflict = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", existingEmail, "role", "HIJO"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Create two invitations in family A (default HIJO visibility -> OWN_ONLY) and one in family B
        ResponseEntity<JsonNode> inv1 = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "pending1-" + UUID.randomUUID() + "@familion.test", "role", "HIJO"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(inv1.getBody().path("familyVisibility").asText()).isEqualTo("OWN_ONLY");
        UUID inv1Id = UUID.fromString(inv1.getBody().path("id").asText());

        ResponseEntity<JsonNode> inv2 = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "expired-" + UUID.randomUUID() + "@familion.test", "role", "HIJO"))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID inv2Id = UUID.fromString(inv2.getBody().path("id").asText());
        jdbcTemplate.update(
                "UPDATE family_invitations SET expires_at = NOW() - INTERVAL '1 hour' WHERE id = ?",
                inv2Id);

        client().post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultBBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "other-fam-" + UUID.randomUUID() + "@familion.test", "role", "HIJO"))
                .retrieve()
                .toEntity(JsonNode.class);

        // GET /api/v1/family/invitations only returns non-expired pending invitation of family A
        ResponseEntity<JsonNode> list = client()
                .get()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody().get(0).path("id").asText()).isEqualTo(inv1Id.toString());

        // Family B cannot delete family A's invitation -> 404
        ResponseEntity<Void> otherDelete = client()
                .delete()
                .uri("/api/v1/family/invitations/{id}", inv1Id)
                .header(HttpHeaders.AUTHORIZATION, adultBBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toBodilessEntity();
        assertThat(otherDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Family A deletes its invitation -> 204
        ResponseEntity<Void> deleted = client()
                .delete()
                .uri("/api/v1/family/invitations/{id}", inv1Id)
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> listAfterDelete = client()
                .get()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listAfterDelete.getBody()).isEmpty();
    }

    @Test
    void publicPreviewAndRegisterInviteCreateChildIssueTokensAndInvalidateTokenReuse() {
        UUID familyId = fixtures.createFamily("Familia Rossi");
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-rossi-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childEmail = "luca-" + UUID.randomUUID() + "@familion.test";

        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", childEmail,
                        "role", "HIJO",
                        "familyVisibility", "FAMILY_SUMMARY"))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID invitationId = UUID.fromString(created.getBody().path("id").asText());
        String inviteUrl = created.getBody().path("inviteUrl").asText();
        String rawToken = inviteUrl.substring(inviteUrl.indexOf("token=") + "token=".length());

        // Public GET /api/v1/auth/invitations/{token} works without Authorization header
        ResponseEntity<JsonNode> preview = client()
                .get()
                .uri("/api/v1/auth/invitations/{token}", rawToken)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getBody()).isNotNull();
        assertThat(preview.getBody().path("email").asText()).isEqualTo(childEmail);
        assertThat(preview.getBody().path("role").asText()).isEqualTo("HIJO");
        assertThat(preview.getBody().path("familyName").asText()).isEqualTo("Familia Rossi");
        assertThat(Instant.parse(preview.getBody().path("expiresAt").asText()))
                .isAfter(Instant.now());

        // Weak password rejected -> 400
        ResponseEntity<JsonNode> weakPass = client()
                .post()
                .uri("/api/v1/auth/register-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "token", rawToken,
                        "fullName", "Luca Rossi",
                        "password", "short"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(weakPass.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Public POST /api/v1/auth/register-invite creates child and returns SessionTokens
        ResponseEntity<JsonNode> registered = client()
                .post()
                .uri("/api/v1/auth/register-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "token", rawToken,
                        "fullName", "Luca Rossi",
                        "password", "StrongPass123"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode tokensBody = registered.getBody();
        assertThat(tokensBody).isNotNull();
        assertThat(tokensBody.path("requires2fa").asBoolean()).isFalse();
        assertThat(tokensBody.path("challengeToken").isNull()).isTrue();
        assertThat(tokensBody.path("accessToken").asText()).isNotBlank();
        assertThat(tokensBody.path("accessTokenExpiresIn").asLong()).isPositive();
        assertThat(tokensBody.path("refreshToken").asText()).isNotBlank();
        assertThat(tokensBody.path("refreshTokenExpiresIn").asLong()).isPositive();
        assertThat(tokensBody.path("deviceToken").isNull()).isTrue();

        // Issued token works against /api/v1/auth/me with expected child role, visibility, and verified email
        ResponseEntity<JsonNode> me = client()
                .get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokensBody.path("accessToken").asText())
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().path("email").asText()).isEqualTo(childEmail);
        assertThat(me.getBody().path("fullName").asText()).isEqualTo("Luca Rossi");
        assertThat(me.getBody().path("role").asText()).isEqualTo("HIJO");
        assertThat(me.getBody().path("familyVisibility").asText()).isEqualTo("FAMILY_SUMMARY");
        assertThat(me.getBody().path("emailVerified").asBoolean()).isTrue();
        assertThat(me.getBody().path("family").path("id").asText()).isEqualTo(familyId.toString());

        // Invitation is marked accepted in DB
        Timestamp acceptedAt = jdbcTemplate.queryForObject(
                "SELECT accepted_at FROM family_invitations WHERE id = ?",
                Timestamp.class,
                invitationId);
        assertThat(acceptedAt).isNotNull();

        // Reusing accepted token returns 404 on both preview and register-invite
        ResponseEntity<JsonNode> previewAfter = client()
                .get()
                .uri("/api/v1/auth/invitations/{token}", rawToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(previewAfter.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> reuseRegister = client()
                .post()
                .uri("/api/v1/auth/register-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "token", rawToken,
                        "fullName", "Luca Again",
                        "password", "StrongPass123"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(reuseRegister.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void expiredOrUnknownInvitationTokenReturns404OnPreviewAndRegister() {
        UUID familyId = fixtures.createFamily("Familia Expired");
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-exp-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/family/invitations")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "expired-tok-" + UUID.randomUUID() + "@familion.test",
                        "role", "HIJO"))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID invitationId = UUID.fromString(created.getBody().path("id").asText());
        String inviteUrl = created.getBody().path("inviteUrl").asText();
        String rawToken = inviteUrl.substring(inviteUrl.indexOf("token=") + "token=".length());

        jdbcTemplate.update(
                "UPDATE family_invitations SET expires_at = NOW() - INTERVAL '5 minutes' WHERE id = ?",
                invitationId);

        ResponseEntity<JsonNode> expiredPreview = client()
                .get()
                .uri("/api/v1/auth/invitations/{token}", rawToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(expiredPreview.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> expiredRegister = client()
                .post()
                .uri("/api/v1/auth/register-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "token", rawToken,
                        "fullName", "Expired User",
                        "password", "StrongPass123"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(expiredRegister.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> unknownPreview = client()
                .get()
                .uri("/api/v1/auth/invitations/{token}", "does-not-exist-token")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(unknownPreview.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static String extractText(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                builder.append(extractText(multipart.getBodyPart(i).getContent()));
            }
            return builder.toString();
        }
        return "";
    }
}
