package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.infrastructure.persistence.NotificationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"familion.economics.sync-on-startup=false", "ECON_SYNC_CRON=-"})
class ActivityFeedIT extends IntegrationTestBase {

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID familyId;
    private UUID actorId;
    private UUID otherAdultId;
    private UUID childId;
    private String actorBearer;

    @BeforeEach
    void setUp() {
        familyId = fixtures.createFamily("Activity Family " + UUID.randomUUID());
        actorId = fixtures.createUser(familyId, "actor_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        otherAdultId = fixtures.createUser(familyId, "other_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childId = fixtures.createUser(familyId, "child_" + UUID.randomUUID() + "@familion.local", Role.HIJO, FamilyVisibility.OWN_ONLY);
        actorBearer = tokens.bearer(actorId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    private List<Notification> activityOf(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()).getContent().stream()
                .filter(n -> n.getCategory() == NotificationCategory.ACTIVITY)
                .toList();
    }

    @Test
    void creatingAnAccount_notifiesOtherAdultsUnread_andActorAsRead_butNotChildren() {
        client().post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, actorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Caja Ahorro", "accountType", "BANK", "currencyCode", "ARS"))
                .retrieve()
                .toBodilessEntity();

        List<Notification> other = activityOf(otherAdultId);
        assertThat(other).hasSize(1);
        Notification n = other.get(0);
        assertThat(n.getTitle()).endsWith("creó una cuenta");
        assertThat(n.getBody()).isEqualTo("Caja Ahorro");
        assertThat(n.getReadAt()).isNull();
        assertThat(n.getMetadata())
                .containsEntry("link", "/accounts")
                .containsEntry("actorUserId", actorId.toString())
                .containsEntry("action", "POST");

        List<Notification> own = activityOf(actorId);
        assertThat(own).hasSize(1);
        assertThat(own.get(0).getReadAt()).isNotNull();

        assertThat(activityOf(childId)).isEmpty();
    }

    @Test
    void failedMutation_producesNoActivity() {
        client().post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, actorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", ""))
                .retrieve()
                .onStatus(status -> true, (req, res) -> {})
                .toBodilessEntity();

        assertThat(activityOf(otherAdultId)).isEmpty();
        assertThat(activityOf(actorId)).isEmpty();
    }

    @Test
    void markingNotificationsRead_isNotItselfActivity() {
        client().post()
                .uri("/api/v1/notifications/read-all")
                .header(HttpHeaders.AUTHORIZATION, actorBearer)
                .retrieve()
                .toBodilessEntity();

        assertThat(activityOf(actorId)).isEmpty();
        assertThat(activityOf(otherAdultId)).isEmpty();
    }
}
