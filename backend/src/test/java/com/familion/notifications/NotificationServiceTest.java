package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.NotFoundException;
import com.familion.notifications.application.NotificationService;
import com.familion.notifications.application.PushSubscriptionService;
import com.familion.notifications.application.WebPushClient;
import com.familion.notifications.application.WebPushProperties;
import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.domain.PushSubscription;
import com.familion.notifications.infrastructure.persistence.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushSubscriptionService pushSubscriptionService;

    @Mock
    private WebPushClient webPushClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    private WebPushProperties disabledWebPushProperties =
            new WebPushProperties("", "", "mailto:test@familion.local");

    private WebPushProperties enabledWebPushProperties =
            new WebPushProperties("BNcTestPublicKey", "testPrivateKey", "mailto:test@familion.local");

    private NotificationService serviceWithPushDisabled;
    private NotificationService serviceWithPushEnabled;

    @BeforeEach
    void setUp() {
        serviceWithPushDisabled =
                new NotificationService(
                        notificationRepository,
                        pushSubscriptionService,
                        disabledWebPushProperties,
                        webPushClient,
                        objectMapper);

        serviceWithPushEnabled =
                new NotificationService(
                        notificationRepository,
                        pushSubscriptionService,
                        enabledWebPushProperties,
                        webPushClient,
                        objectMapper);
    }

    @Test
    void notify_whenDedupeKeyAlreadyExists_skipsCreation() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String dedupeKey = "fixed:123:2026-11";
        Map<String, Object> metadata = Map.of("dedupeKey", dedupeKey, "link", "/plan/recurring");

        when(notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)).thenReturn(true);

        Optional<Notification> result =
                serviceWithPushDisabled.notify(
                        userId,
                        familyId,
                        NotificationCategory.FIXED_EXPENSE_DUE,
                        "Vence Netflix",
                        "Vence Netflix",
                        metadata);

        assertThat(result).isEmpty();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notify_whenDedupeKeyDoesNotExist_savesNotification() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String dedupeKey = "fixed:123:2026-11";
        Map<String, Object> metadata = Map.of("dedupeKey", dedupeKey, "link", "/plan/recurring");

        when(notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Optional<Notification> result =
                serviceWithPushDisabled.notify(
                        userId,
                        familyId,
                        NotificationCategory.FIXED_EXPENSE_DUE,
                        "Vence Netflix",
                        "Vence Netflix",
                        metadata);

        assertThat(result).isPresent();
        assertThat(result.get().getCategory()).isEqualTo(NotificationCategory.FIXED_EXPENSE_DUE);
        assertThat(result.get().getTitle()).isEqualTo("Vence Netflix");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markAsRead_whenOwnedByUser_marksRead() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        Notification n =
                new Notification(
                        UUID.randomUUID(),
                        userId,
                        NotificationCategory.SYSTEM,
                        "Title",
                        "Body",
                        null,
                        Instant.now());

        when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification updated = serviceWithPushDisabled.markAsRead(userId, notifId);

        assertThat(updated.getReadAt()).isNotNull();
        verify(notificationRepository).save(n);
    }

    @Test
    void markAsRead_whenNotOwnedByUser_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithPushDisabled.markAsRead(userId, notifId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void countUnread_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByUserIdAndReadAtIsNull(userId)).thenReturn(5L);

        long count = serviceWithPushDisabled.countUnread(userId);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void sendPush_withVapidEmpty_doesNotAttemptToSend() {
        UUID userId = UUID.randomUUID();

        serviceWithPushDisabled.sendPushNotifications(
                userId, "Test Title", "Test Body", "/transactions?id=1");

        verify(pushSubscriptionService, never()).findActiveByUserId(any());
        verify(webPushClient, never()).send(any(), any());
    }

    @Test
    void sendPush_withVapidConfigured_sendsPayloadToActiveSubscriptions() {
        UUID userId = UUID.randomUUID();
        PushSubscription sub =
                new PushSubscription(
                        userId,
                        "https://fcm.googleapis.com/fcm/send/test",
                        "testP256dh",
                        "testAuth",
                        "Chrome",
                        true,
                        Instant.now());

        when(pushSubscriptionService.findActiveByUserId(userId)).thenReturn(List.of(sub));
        when(webPushClient.send(eq(sub), any())).thenReturn(201);

        serviceWithPushEnabled.sendPushNotifications(
                userId, "Alerta", "Cuerpo de alerta", "/vehicles/123");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(webPushClient).send(eq(sub), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"title\":\"Alerta\"");
        assertThat(payload).contains("\"body\":\"Cuerpo de alerta\"");
        assertThat(payload).contains("\"link\":\"/vehicles/123\"");
    }

    @Test
    void sendPush_when410Gone_deactivatesSubscription() {
        UUID userId = UUID.randomUUID();
        PushSubscription sub =
                new PushSubscription(
                        userId,
                        "https://fcm.googleapis.com/fcm/send/expired",
                        "testP256dh",
                        "testAuth",
                        "Chrome",
                        true,
                        Instant.now());

        when(pushSubscriptionService.findActiveByUserId(userId)).thenReturn(List.of(sub));
        when(webPushClient.send(eq(sub), any())).thenReturn(410);

        serviceWithPushEnabled.sendPushNotifications(
                userId, "Alerta", "Cuerpo de alerta", "/vehicles/123");

        verify(pushSubscriptionService).deactivateById(sub.getId());
    }
}
