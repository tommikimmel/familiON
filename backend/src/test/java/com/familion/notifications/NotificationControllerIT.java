package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.notifications.application.NotificationService;
import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.infrastructure.persistence.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class NotificationControllerIT extends IntegrationTestBase {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    private UUID familyId;
    private UUID user1Id;
    private UUID user2Id;
    private String user1Bearer;
    private String user2Bearer;

    @BeforeEach
    void setUp() {
        familyId = fixtures.createFamily("Notif Family " + UUID.randomUUID());
        user1Id = fixtures.createUser(familyId, "user1_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        user2Id = fixtures.createUser(familyId, "user2_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        user1Bearer = tokens.bearer(user1Id, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        user2Bearer = tokens.bearer(user2Id, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    private RestClient patchClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void listNotifications_andUnreadCount_andDedupe() {
        // Create 2 notifications for user 1 (one with dedupeKey)
        notificationService.notify(
                user1Id,
                familyId,
                NotificationCategory.FIXED_EXPENSE_DUE,
                "Vence Netflix",
                "Vence Netflix el 15/11",
                Map.of("dedupeKey", "fixed:netflix:2026-11", "link", "/plan/recurring"));

        // Duplicate call should be skipped
        notificationService.notify(
                user1Id,
                familyId,
                NotificationCategory.FIXED_EXPENSE_DUE,
                "Vence Netflix",
                "Vence Netflix el 15/11",
                Map.of("dedupeKey", "fixed:netflix:2026-11", "link", "/plan/recurring"));

        notificationService.notify(
                user1Id,
                familyId,
                NotificationCategory.SYSTEM,
                "Bienvenido",
                "Bienvenido a FamiliON",
                Map.of("link", "/"));

        // Create 1 notification for user 2
        notificationService.notify(
                user2Id,
                familyId,
                NotificationCategory.SYSTEM,
                "Hola User 2",
                "Cuerpo",
                null);

        // User 1 unread count should be 2
        ResponseEntity<JsonNode> countResp = client()
                .get()
                .uri("/api/v1/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(countResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countResp.getBody().path("count").asLong()).isEqualTo(2L);

        // User 1 lists notifications -> 2 items in page content
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/notifications?unreadOnly=false&page=0&size=10")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = listResp.getBody().path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(2);
    }

    @Test
    void markAsRead_ownedVsOtherUser() {
        Notification notif = notificationService.notify(
                user1Id,
                familyId,
                NotificationCategory.SYSTEM,
                "Aviso",
                "Aviso de prueba",
                null).orElseThrow();

        // User 2 trying to mark user 1's notification as read -> 404
        ResponseEntity<JsonNode> notFoundResp = patchClient()
                .patch()
                .uri("/api/v1/notifications/" + notif.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, user2Bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(notFoundResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // User 1 marks own notification as read -> 200
        ResponseEntity<JsonNode> okResp = patchClient()
                .patch()
                .uri("/api/v1/notifications/" + notif.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(okResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(okResp.getBody().path("readAt").isNull()).isFalse();

        // Unread only now returns 0 items for user 1
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/notifications?unreadOnly=true")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listResp.getBody().path("content").size()).isEqualTo(0);
    }

    @Test
    void readAll_marksAllUnreadAsRead() {
        notificationService.notify(user1Id, familyId, NotificationCategory.SYSTEM, "1", "1", null);
        notificationService.notify(user1Id, familyId, NotificationCategory.SYSTEM, "2", "2", null);

        ResponseEntity<Void> readAllResp = client()
                .post()
                .uri("/api/v1/notifications/read-all")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(readAllResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> countResp = client()
                .get()
                .uri("/api/v1/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(countResp.getBody().path("count").asLong()).isEqualTo(0L);
    }

    @Test
    void pushConfig_andSubscriptionsLifecycle() {
        // GET /push-config
        ResponseEntity<JsonNode> configResp = client()
                .get()
                .uri("/api/v1/notifications/push-config")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(configResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // By default without VAPID keys, publicKey is null
        assertThat(configResp.getBody().path("publicKey").isNull()).isTrue();

        // POST /push-subscriptions
        String endpoint = "https://fcm.googleapis.com/fcm/send/device-" + UUID.randomUUID();
        Map<String, Object> subBody = Map.of(
                "endpoint", endpoint,
                "keys", Map.of(
                        "p256dh", "test-p256dh-key",
                        "auth", "test-auth-key"
                ),
                "userAgent", "Mozilla/5.0 Chrome"
        );

        ResponseEntity<Void> createResp = client()
                .post()
                .uri("/api/v1/notifications/push-subscriptions")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(subBody)
                .retrieve()
                .toBodilessEntity();
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint)).isPresent();
        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint).get().isActive()).isTrue();

        // DELETE /push-subscriptions
        ResponseEntity<Void> delResp = patchClient()
                .method(org.springframework.http.HttpMethod.DELETE)
                .uri("/api/v1/notifications/push-subscriptions")
                .header(HttpHeaders.AUTHORIZATION, user1Bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("endpoint", endpoint))
                .retrieve()
                .toBodilessEntity();
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint).get().isActive()).isFalse();
    }
}
