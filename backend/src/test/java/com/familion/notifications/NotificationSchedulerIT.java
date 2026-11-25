package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.finance.recurring.application.api.RecurringDue;
import com.familion.finance.recurring.application.api.RecurringExpenseQueryApi;
import com.familion.notifications.application.NotificationService;
import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.infrastructure.external.NotificationScheduler;
import com.familion.notifications.infrastructure.persistence.NotificationRepository;
import com.familion.vehicles.application.api.VehicleAlertQueryApi;
import com.familion.vehicles.application.api.VehicleAlertRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

/**
 * Builds the scheduler by hand with a fixed clock and mocked query APIs so the test reuses the
 * shared integration context instead of starting a dedicated one (and another connection pool).
 */
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class NotificationSchedulerIT extends IntegrationTestBase {

    // 2026-11-25 08:00:00 ART
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-25T11:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));

    private final RecurringExpenseQueryApi recurringExpenseQueryApi = mock(RecurringExpenseQueryApi.class);
    private final VehicleAlertQueryApi vehicleAlertQueryApi = mock(VehicleAlertQueryApi.class);

    @Autowired
    private FamilyQueryApi familyQueryApi;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    private NotificationScheduler notificationScheduler;

    private UUID familyId;
    private UUID adultId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        notificationScheduler =
                new NotificationScheduler(
                        FIXED_CLOCK, recurringExpenseQueryApi, vehicleAlertQueryApi, familyQueryApi, notificationService);
        familyId = fixtures.createFamily("Scheduler Family " + UUID.randomUUID());
        adultId = fixtures.createUser(familyId, "adult_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childId = fixtures.createUser(familyId, "child_" + UUID.randomUUID() + "@familion.local", Role.HIJO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void recurringExpenseJob_generatesNotificationsOnce_andDeduplicatesOnRerun() {
        UUID recurringId = UUID.randomUUID();
        RecurringDue due =
                new RecurringDue(
                        recurringId,
                        familyId,
                        "Seguro Hogar",
                        LocalDate.of(2026, 11, 27),
                        new BigDecimal("12500.00"),
                        "ARS");

        when(recurringExpenseQueryApi.pendingDueBetween(any(), any()))
                .thenReturn(List.of(due));

        // First run
        notificationScheduler.processDueRecurringExpenses();

        verify(recurringExpenseQueryApi).pendingDueBetween(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 28));
        List<Notification> adultNotifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(adultId, Pageable.unpaged()).getContent();
        List<Notification> childNotifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(childId, Pageable.unpaged()).getContent();

        assertThat(adultNotifs).hasSize(1);
        assertThat(childNotifs).isEmpty();

        Notification n = adultNotifs.get(0);
        assertThat(n.getCategory()).isEqualTo(NotificationCategory.FIXED_EXPENSE_DUE);
        assertThat(n.getTitle()).contains("Vence Seguro Hogar el 27/11: $12.500");
        assertThat(n.getMetadata().get("dedupeKey")).isEqualTo("fixed:" + recurringId + ":2026-11");
        assertThat(n.getMetadata().get("link")).isEqualTo("/plan/recurring");

        // Second run: re-execution must NOT duplicate
        notificationScheduler.processDueRecurringExpenses();

        List<Notification> adultNotifsAfterRerun =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(adultId, Pageable.unpaged()).getContent();
        assertThat(adultNotifsAfterRerun).hasSize(1);
    }

    @Test
    void vehicleAlertJob_generatesNotificationsOnce_andDeduplicatesOnRerun() {
        UUID vehicleId = UUID.randomUUID();
        VehicleAlertRef alert =
                new VehicleAlertRef(
                        familyId,
                        vehicleId,
                        "Cronos",
                        "OIL_CHANGE",
                        "Cambio de aceite próximo",
                        LocalDate.of(2026, 12, 1),
                        10000,
                        "WARNING");

        when(vehicleAlertQueryApi.activeAlerts()).thenReturn(List.of(alert));

        // First run
        notificationScheduler.processVehicleAlerts();

        List<Notification> adultNotifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(adultId, Pageable.unpaged()).getContent();
        List<Notification> childNotifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(childId, Pageable.unpaged()).getContent();

        assertThat(adultNotifs).hasSize(1);
        assertThat(childNotifs).isEmpty();

        Notification n = adultNotifs.get(0);
        assertThat(n.getCategory()).isEqualTo(NotificationCategory.VEHICLE_ALERT);
        assertThat(n.getTitle()).isEqualTo("Cambio de aceite próximo");
        assertThat(n.getMetadata().get("dedupeKey")).isEqualTo("vehicle:" + vehicleId + ":OIL_CHANGE:2026-12-01");
        assertThat(n.getMetadata().get("link")).isEqualTo("/vehicles/" + vehicleId);

        // Second run: re-execution must NOT duplicate
        notificationScheduler.processVehicleAlerts();

        List<Notification> adultNotifsAfterRerun =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(adultId, Pageable.unpaged()).getContent();
        assertThat(adultNotifsAfterRerun).hasSize(1);
    }
}
