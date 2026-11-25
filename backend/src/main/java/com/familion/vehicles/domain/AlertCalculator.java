package com.familion.vehicles.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AlertCalculator {

    private static final DateTimeFormatter DAY_MONTH_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    private AlertCalculator() {}

    public static List<VehicleAlert> computeAlerts(
            Vehicle vehicle,
            LocalDate today,
            List<LatestMaintenanceDue> latestMaintenanceDues) {
        Objects.requireNonNull(vehicle, "vehicle must not be null");
        Objects.requireNonNull(today, "today must not be null");

        List<VehicleAlert> alerts = new ArrayList<>();

        if (vehicle.getVtvExpirationDate() != null) {
            LocalDate dueDate = vehicle.getVtvExpirationDate();
            VehicleAlertSeverity severity = calculateDateSeverity(dueDate, today);
            String title = "VTV vence el " + dueDate.format(DAY_MONTH_FORMATTER);
            alerts.add(new VehicleAlert(
                    VehicleAlertKind.VTV.name(),
                    title,
                    dueDate,
                    null,
                    severity.name()));
        }

        if (vehicle.getInsuranceExpiration() != null) {
            LocalDate dueDate = vehicle.getInsuranceExpiration();
            VehicleAlertSeverity severity = calculateDateSeverity(dueDate, today);
            String title = "Seguro vence el " + dueDate.format(DAY_MONTH_FORMATTER);
            alerts.add(new VehicleAlert(
                    VehicleAlertKind.INSURANCE.name(),
                    title,
                    dueDate,
                    null,
                    severity.name()));
        }

        if (latestMaintenanceDues != null) {
            for (LatestMaintenanceDue due : latestMaintenanceDues) {
                if (due.nextDueKm() != null) {
                    int dueKm = due.nextDueKm();
                    int currentKm = vehicle.getCurrentOdometerKm();
                    int remainingKm = dueKm - currentKm;
                    VehicleAlertSeverity severity;
                    if (dueKm <= currentKm) {
                        severity = VehicleAlertSeverity.OVERDUE;
                    } else if (remainingKm <= 1000) {
                        severity = VehicleAlertSeverity.WARNING;
                    } else {
                        severity = VehicleAlertSeverity.INFO;
                    }

                    String title = remainingKm >= 0
                            ? due.maintenanceType().getLabel() + " en " + remainingKm + " km"
                            : due.maintenanceType().getLabel() + " superado por " + Math.abs(remainingKm) + " km";

                    alerts.add(new VehicleAlert(
                            VehicleAlertKind.SERVICE_KM.name(),
                            title,
                            null,
                            dueKm,
                            severity.name()));
                }

                if (due.nextDueDate() != null) {
                    LocalDate dueDate = due.nextDueDate();
                    VehicleAlertSeverity severity = calculateDateSeverity(dueDate, today);
                    String title = due.maintenanceType().getLabel() + " vence el "
                            + dueDate.format(DAY_MONTH_FORMATTER);
                    alerts.add(new VehicleAlert(
                            VehicleAlertKind.SERVICE_DATE.name(),
                            title,
                            dueDate,
                            null,
                            severity.name()));
                }
            }
        }

        alerts.sort(Comparator.comparingInt(a -> severityOrder(a.severity())));
        return alerts;
    }

    private static VehicleAlertSeverity calculateDateSeverity(LocalDate dueDate, LocalDate today) {
        if (dueDate.isBefore(today)) {
            return VehicleAlertSeverity.OVERDUE;
        } else if (!dueDate.isAfter(today.plusDays(30))) {
            return VehicleAlertSeverity.WARNING;
        } else {
            return VehicleAlertSeverity.INFO;
        }
    }

    private static int severityOrder(String severity) {
        return switch (severity) {
            case "OVERDUE" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    public record LatestMaintenanceDue(
            MaintenanceType maintenanceType,
            Integer nextDueKm,
            LocalDate nextDueDate) {}
}
