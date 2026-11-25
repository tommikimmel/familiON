package com.familion.notifications.application;

import com.familion.common.error.NotFoundException;
import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.domain.PushSubscription;
import com.familion.notifications.infrastructure.persistence.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushProperties webPushProperties;
    private final WebPushClient webPushClient;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationRepository notificationRepository,
            PushSubscriptionService pushSubscriptionService,
            WebPushProperties webPushProperties,
            WebPushClient webPushClient,
            ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.pushSubscriptionService = pushSubscriptionService;
        this.webPushProperties = webPushProperties;
        this.webPushClient = webPushClient;
        this.objectMapper = objectMapper;
    }

    public Optional<Notification> notify(
            UUID userId,
            UUID familyId,
            NotificationCategory category,
            String title,
            String body,
            Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("dedupeKey")) {
            String dedupeKey = String.valueOf(metadata.get("dedupeKey"));
            if (!dedupeKey.isBlank() && notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
                log.debug("Skipping duplicate notification for user {} dedupeKey {}", userId, dedupeKey);
                return Optional.empty();
            }
        }

        Notification notification =
                new Notification(familyId, userId, category, title, body, metadata, Instant.now());
        notification = notificationRepository.save(notification);

        // Activity feed entries are in-app only: pushing every change would spam devices.
        if (webPushProperties.isConfigured() && category != NotificationCategory.ACTIVITY) {
            String link = metadata != null && metadata.containsKey("link")
                    ? String.valueOf(metadata.get("link"))
                    : null;
            CompletableFuture.runAsync(() -> sendPushNotifications(userId, title, body, link));
        }

        return Optional.of(notification);
    }

    public void sendPushNotifications(UUID userId, String title, String body, String link) {
        if (!webPushProperties.isConfigured()) {
            return;
        }
        List<PushSubscription> activeSubs = pushSubscriptionService.findActiveByUserId(userId);
        if (activeSubs.isEmpty()) {
            return;
        }
        try {
            Map<String, String> payloadMap = new LinkedHashMap<>();
            payloadMap.put("title", title);
            payloadMap.put("body", body);
            if (link != null) {
                payloadMap.put("link", link);
            }
            String payload = objectMapper.writeValueAsString(payloadMap);
            for (PushSubscription sub : activeSubs) {
                int status = webPushClient.send(sub, payload);
                if (status == 404 || status == 410) {
                    pushSubscriptionService.deactivateById(sub.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch push notification for user {}", userId, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> listNotifications(UUID userId, boolean unreadOnly, Pageable pageable) {
        if (unreadOnly) {
            return notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    public Notification markAsRead(UUID userId, UUID notificationId) {
        Notification notification =
                notificationRepository
                        .findByIdAndUserId(notificationId, userId)
                        .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));
        notification.markAsRead(Instant.now());
        return notificationRepository.save(notification);
    }

    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId, Instant.now());
    }
}
