package com.familion.notifications.infrastructure.web;

import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationCategory category,
        String title,
        String body,
        Map<String, Object> metadata,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getCategory(),
                n.getTitle(),
                n.getBody(),
                n.getMetadata(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}
