package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class MeAvatarIT extends IntegrationTestBase {

    private static final byte[] PNG_HEADER = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    private String ownerBearer;
    private String otherMemberBearer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID familyId = fixtures.createFamily("Familia Avatar " + suffix);
        UUID ownerId = fixtures.createUser(
                familyId, "owner." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        UUID otherId = fixtures.createUser(
                familyId, "other." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        ownerBearer = tokens.bearer(ownerId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        otherMemberBearer = tokens.bearer(otherId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void uploadReplaceDownloadAndRemoveOwnAvatar() {
        assertThat(get(ownerBearer, "/api/v1/auth/me").getBody().path("avatarUpdatedAt").isNull())
                .isTrue();
        assertThat(getAvatar(ownerBearer).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> uploaded = upload(ownerBearer, PNG_HEADER, "../evil.txt");
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploaded.getBody().path("avatarUpdatedAt").asText()).isNotBlank();

        ResponseEntity<byte[]> png = getAvatar(ownerBearer);
        assertThat(png.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(png.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(png.getBody()).isEqualTo(PNG_HEADER);

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        assertThat(upload(ownerBearer, jpeg, "photo.jpg").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<byte[]> replaced = getAvatar(ownerBearer);
        assertThat(replaced.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(replaced.getBody()).isEqualTo(jpeg);

        // Another family member never sees nor changes the owner's photo.
        assertThat(getAvatar(otherMemberBearer).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> removed = client()
                .delete()
                .uri("/api/v1/auth/me/avatar")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removed.getBody().path("avatarUpdatedAt").isNull()).isTrue();
        assertThat(getAvatar(ownerBearer).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsNonImageWith415() {
        ResponseEntity<JsonNode> response = upload(
                ownerBearer, "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8), "x.png");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsImageOverTwoMegabytesWith413() {
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG_HEADER, 0, oversized, 0, PNG_HEADER.length);
        assertThat(upload(ownerBearer, oversized, "big.png").getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void requiresAuthentication() {
        assertThat(getAvatar(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> upload(String bearer, byte[] bytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return client()
                .put()
                .uri("/api/v1/auth/me/avatar")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private ResponseEntity<byte[]> getAvatar(String bearer) {
        return client()
                .get()
                .uri("/api/v1/auth/me/avatar")
                .headers(h -> {
                    if (bearer != null) {
                        h.set(HttpHeaders.AUTHORIZATION, bearer);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(byte[].class);
    }

    private ResponseEntity<JsonNode> get(String bearer, String uri) {
        return client()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
