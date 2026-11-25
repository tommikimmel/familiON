package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class SystemVersionControllerIT extends IntegrationTestBase {

    private UUID familyId;
    private UUID userId;
    private String userBearer;

    @BeforeEach
    void setUp() {
        familyId = fixtures.createFamily("System Version Family " + UUID.randomUUID());
        userId = fixtures.createUser(familyId, "sys_user_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        userBearer = tokens.bearer(userId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void getVersion_withoutToken_returns200AndLatestRelease() {
        ResponseEntity<JsonNode> resp = client()
                .get()
                .uri("/api/v1/system/version")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("version").asText()).isEqualTo("0.1.0");

        JsonNode latest = body.path("latestRelease");
        assertThat(latest.isNull()).isFalse();
        assertThat(latest.path("version").asText()).isEqualTo("0.5.0");
        assertThat(latest.path("title").asText()).isEqualTo("Notificaciones y PWA");
        assertThat(latest.path("releasedAt").asText()).isNotBlank();
    }

    @Test
    void getChangelog_requiresAuthentication_returnsAllReleasesOrderedDesc() {
        // Without auth -> 401
        ResponseEntity<JsonNode> unauth = client()
                .get()
                .uri("/api/v1/system/changelog")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // With auth -> 200
        ResponseEntity<JsonNode> resp = client()
                .get()
                .uri("/api/v1/system/changelog")
                .header(HttpHeaders.AUTHORIZATION, userBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode list = resp.getBody();
        assertThat(list).isNotNull();
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isGreaterThanOrEqualTo(5);

        // First item must be 0.5.0
        assertThat(list.get(0).path("version").asText()).isEqualTo("0.5.0");
        assertThat(list.get(0).path("title").asText()).isEqualTo("Notificaciones y PWA");
        assertThat(list.get(0).path("changelogMarkdown").asText()).contains("### Novedades");

        // Last item should be 0.1.0
        assertThat(list.get(list.size() - 1).path("version").asText()).isEqualTo("0.1.0");
    }
}
