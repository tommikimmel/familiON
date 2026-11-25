package com.familion.vehicles.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertCalculatorTest {

    private final LocalDate today = LocalDate.of(2026, 11, 24);

    @Test
    @DisplayName("VTV and Insurance alert severities and Spanish titles")
    void vtvAndInsuranceAlerts() {
        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                "Gol Trend",
                "Volkswagen",
                "Gol",
                2020,
                "AA123BB",
                FuelType.NAFTA_SUPER,
                null,
                50000,
                LocalDate.of(2026, 12, 12),
                LocalDate.of(2026, 11, 1),
                "La Segunda",
                null);

        List<VehicleAlert> alerts = AlertCalculator.computeAlerts(vehicle, today, List.of());

        assertThat(alerts).hasSize(2);

        VehicleAlert insuranceAlert = alerts.stream()
                .filter(a -> a.kind().equals("INSURANCE"))
                .findFirst()
                .orElseThrow();
        assertThat(insuranceAlert.severity()).isEqualTo("OVERDUE");
        assertThat(insuranceAlert.title()).isEqualTo("Seguro vence el 01/11");

        VehicleAlert vtvAlert = alerts.stream()
                .filter(a -> a.kind().equals("VTV"))
                .findFirst()
                .orElseThrow();
        assertThat(vtvAlert.severity()).isEqualTo("WARNING");
        assertThat(vtvAlert.title()).isEqualTo("VTV vence el 12/12");
    }

    @Test
    @DisplayName("Maintenance alerts by km and date with Spanish labels")
    void maintenanceAlertsByKmAndDate() {
        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                "Kangoo",
                "Renault",
                "Kangoo",
                2022,
                "AF456CD",
                FuelType.DIESEL,
                null,
                9200,
                null,
                null,
                null,
                null);

        AlertCalculator.LatestMaintenanceDue oilChange = new AlertCalculator.LatestMaintenanceDue(
                MaintenanceType.OIL_CHANGE,
                10000,
                null);

        AlertCalculator.LatestMaintenanceDue timingBelt = new AlertCalculator.LatestMaintenanceDue(
                MaintenanceType.TIMING_BELT,
                15000,
                LocalDate.of(2026, 12, 10));

        AlertCalculator.LatestMaintenanceDue brakes = new AlertCalculator.LatestMaintenanceDue(
                MaintenanceType.BRAKES,
                9000,
                null);

        List<VehicleAlert> alerts = AlertCalculator.computeAlerts(
                vehicle, today, List.of(oilChange, timingBelt, brakes));

        VehicleAlert oilAlert = alerts.stream()
                .filter(a -> a.dueKm() != null && a.dueKm() == 10000)
                .findFirst()
                .orElseThrow();
        assertThat(oilAlert.severity()).isEqualTo("WARNING");
        assertThat(oilAlert.title()).isEqualTo("Cambio de aceite en 800 km");

        VehicleAlert brakesAlert = alerts.stream()
                .filter(a -> a.dueKm() != null && a.dueKm() == 9000)
                .findFirst()
                .orElseThrow();
        assertThat(brakesAlert.severity()).isEqualTo("OVERDUE");
        assertThat(brakesAlert.title()).isEqualTo("Frenos superado por 200 km");

        VehicleAlert timingBeltKmAlert = alerts.stream()
                .filter(a -> a.dueKm() != null && a.dueKm() == 15000)
                .findFirst()
                .orElseThrow();
        assertThat(timingBeltKmAlert.severity()).isEqualTo("INFO");

        VehicleAlert timingBeltDateAlert = alerts.stream()
                .filter(a -> a.dueDate() != null && a.dueDate().equals(LocalDate.of(2026, 12, 10)))
                .findFirst()
                .orElseThrow();
        assertThat(timingBeltDateAlert.severity()).isEqualTo("WARNING");
        assertThat(timingBeltDateAlert.title()).isEqualTo("Correa de distribución vence el 10/12");
    }
}
