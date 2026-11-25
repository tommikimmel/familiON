package com.familion.notifications.infrastructure.external;

import com.familion.notifications.application.NotificationService;
import com.familion.notifications.domain.AppRelease;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.infrastructure.persistence.ActiveUserQuery;
import com.familion.notifications.infrastructure.persistence.AppReleaseRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppUpdateStartupListener {

    private static final Logger log = LoggerFactory.getLogger(AppUpdateStartupListener.class);

    private final String appVersion;
    private final AppReleaseRepository releaseRepository;
    private final ActiveUserQuery activeUserQuery;
    private final NotificationService notificationService;

    public AppUpdateStartupListener(
            @Value("${familion.app.version:0.1.0}") String appVersion,
            AppReleaseRepository releaseRepository,
            ActiveUserQuery activeUserQuery,
            NotificationService notificationService) {
        this.appVersion = appVersion;
        this.releaseRepository = releaseRepository;
        this.activeUserQuery = activeUserQuery;
        this.notificationService = notificationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Checking for app release notification for version {}", appVersion);
        releaseRepository
                .findById(appVersion)
                .ifPresent(
                        release -> {
                            String title = "FamiliON v" + appVersion + ": " + release.getTitle();
                            String dedupeKey = "release:" + appVersion;
                            String link = "/changelog";
                            Map<String, Object> metadata = Map.of("link", link, "dedupeKey", dedupeKey);

                            List<ActiveUserQuery.ActiveUser> activeUsers =
                                    activeUserQuery.findAllActiveUsers();
                            log.info(
                                    "Dispatching APP_UPDATE release notification to {} active users",
                                    activeUsers.size());
                            for (ActiveUserQuery.ActiveUser user : activeUsers) {
                                try {
                                    notificationService.notify(
                                            user.userId(),
                                            user.familyId(),
                                            NotificationCategory.APP_UPDATE,
                                            title,
                                            title,
                                            metadata);
                                } catch (Exception e) {
                                    log.error(
                                            "Failed to send APP_UPDATE notification to user {}",
                                            user.userId(),
                                            e);
                                }
                            }
                        });
    }
}
