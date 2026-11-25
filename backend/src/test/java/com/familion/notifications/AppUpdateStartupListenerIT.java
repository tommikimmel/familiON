package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.infrastructure.external.AppUpdateStartupListener;
import com.familion.notifications.infrastructure.persistence.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class AppUpdateStartupListenerIT extends IntegrationTestBase {

    @Autowired
    private AppUpdateStartupListener appUpdateStartupListener;

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID familyId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        familyId = fixtures.createFamily("AppUpdate Family " + UUID.randomUUID());
        userId = fixtures.createUser(familyId, "update_user_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void onApplicationReady_createsAppUpdateNotification_andDeduplicates() {
        // Trigger startup listener
        appUpdateStartupListener.onApplicationReady();

        List<Notification> notifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()).getContent();
        assertThat(notifs).hasSize(1);

        Notification n = notifs.get(0);
        assertThat(n.getCategory()).isEqualTo(NotificationCategory.APP_UPDATE);
        assertThat(n.getTitle()).isEqualTo("FamiliON v0.1.0: Base del proyecto");
        assertThat(n.getMetadata().get("dedupeKey")).isEqualTo("release:0.1.0");

        // Running again must NOT duplicate
        appUpdateStartupListener.onApplicationReady();

        List<Notification> notifsAfterRerun =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()).getContent();
        assertThat(notifsAfterRerun).hasSize(1);
    }
}
