package com.familion.notifications.infrastructure.external;

import com.familion.common.security.Role;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.recurring.application.api.RecurringDue;
import com.familion.finance.recurring.application.api.RecurringExpenseQueryApi;
import com.familion.notifications.application.NotificationService;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.vehicles.application.api.VehicleAlertQueryApi;
import com.familion.vehicles.application.api.VehicleAlertRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter DD_MM_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter YYYY_MM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Clock clock;
    private final RecurringExpenseQueryApi recurringExpenseQueryApi;
    private final VehicleAlertQueryApi vehicleAlertQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final NotificationService notificationService;

    public NotificationScheduler(
            Clock clock,
            RecurringExpenseQueryApi recurringExpenseQueryApi,
            VehicleAlertQueryApi vehicleAlertQueryApi,
            FamilyQueryApi familyQueryApi,
            NotificationService notificationService) {
        this.clock = clock;
        this.recurringExpenseQueryApi = recurringExpenseQueryApi;
        this.vehicleAlertQueryApi = vehicleAlertQueryApi;
        this.familyQueryApi = familyQueryApi;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Argentina/Buenos_Aires")
    public void processDueRecurringExpenses() {
        log.info("Running daily scheduled job for recurring expenses due");
        LocalDate hoy = LocalDate.now(clock.withZone(ZONE));
        LocalDate hoyPlus3 = hoy.plusDays(3);

        List<RecurringDue> dues = recurringExpenseQueryApi.pendingDueBetween(hoy, hoyPlus3);
        for (RecurringDue due : dues) {
            try {
                String ddMM = due.dueDate().format(DD_MM_FORMATTER);
                String formattedAmount = AmountFormatter.format(due.estimatedAmount(), due.currencyCode());
                String text = "Vence " + due.name() + " el " + ddMM + ": " + formattedAmount;
                String yyyyMM = due.dueDate().format(YYYY_MM_FORMATTER);
                String dedupeKey = "fixed:" + due.recurringExpenseId() + ":" + yyyyMM;
                String link = "/plan/recurring";
                Map<String, Object> metadata = Map.of("link", link, "dedupeKey", dedupeKey);

                List<MemberRef> members = familyQueryApi.listMembers(due.familyId());
                for (MemberRef member : members) {
                    if (member.role() == Role.ADULTO) {
                        notificationService.notify(
                                member.userId(),
                                due.familyId(),
                                NotificationCategory.FIXED_EXPENSE_DUE,
                                text,
                                text,
                                metadata);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process recurring expense notification for {}", due.recurringExpenseId(), e);
            }
        }
    }

    @Scheduled(cron = "0 5 8 * * *", zone = "America/Argentina/Buenos_Aires")
    public void processVehicleAlerts() {
        log.info("Running daily scheduled job for vehicle alerts");
        List<VehicleAlertRef> alerts = vehicleAlertQueryApi.activeAlerts();
        for (VehicleAlertRef alert : alerts) {
            try {
                String duePart = alert.dueDate() != null
                        ? alert.dueDate().toString()
                        : (alert.dueKm() != null ? String.valueOf(alert.dueKm()) : "active");
                String dedupeKey = "vehicle:" + alert.vehicleId() + ":" + alert.kind() + ":" + duePart;
                String link = "/vehicles/" + alert.vehicleId();
                Map<String, Object> metadata = Map.of("link", link, "dedupeKey", dedupeKey);

                List<MemberRef> members = familyQueryApi.listMembers(alert.familyId());
                for (MemberRef member : members) {
                    if (member.role() == Role.ADULTO) {
                        notificationService.notify(
                                member.userId(),
                                alert.familyId(),
                                NotificationCategory.VEHICLE_ALERT,
                                alert.title(),
                                alert.title(),
                                metadata);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process vehicle alert notification for {}", alert.vehicleId(), e);
            }
        }
    }
}
