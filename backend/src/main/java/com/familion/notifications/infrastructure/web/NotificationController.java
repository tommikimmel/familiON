package com.familion.notifications.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.notifications.application.NotificationService;
import com.familion.notifications.application.PushSubscriptionService;
import com.familion.notifications.application.WebPushProperties;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushProperties webPushProperties;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(
            NotificationService notificationService,
            PushSubscriptionService pushSubscriptionService,
            WebPushProperties webPushProperties,
            CurrentUserProvider currentUserProvider) {
        this.notificationService = notificationService;
        this.pushSubscriptionService = pushSubscriptionService;
        this.webPushProperties = webPushProperties;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public Page<NotificationResponse> listNotifications(
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        if (pageable.getPageSize() > 50) {
            pageable = PageRequest.of(pageable.getPageNumber(), 50, pageable.getSort());
        }
        UUID userId = currentUserProvider.get().userId();
        return notificationService
                .listNotifications(userId, unreadOnly, pageable)
                .map(NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount() {
        UUID userId = currentUserProvider.get().userId();
        return new UnreadCountResponse(notificationService.countUnread(userId));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable("id") UUID id) {
        UUID userId = currentUserProvider.get().userId();
        return NotificationResponse.from(notificationService.markAsRead(userId, id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        UUID userId = currentUserProvider.get().userId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/push-config")
    public PushConfigResponse getPushConfig() {
        return new PushConfigResponse(webPushProperties.getPublicKey());
    }

    @PostMapping("/push-subscriptions")
    public ResponseEntity<Void> savePushSubscription(
            @Valid @RequestBody PushSubscriptionRequest request) {
        UUID userId = currentUserProvider.get().userId();
        pushSubscriptionService.saveSubscription(
                userId,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth(),
                request.userAgent());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/push-subscriptions")
    public ResponseEntity<Void> deletePushSubscription(
            @Valid @RequestBody PushSubscriptionDeleteRequest request) {
        UUID userId = currentUserProvider.get().userId();
        pushSubscriptionService.deactivateSubscription(userId, request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
