package com.familion.vehicles.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FullTankConsumptionCalculatorTest {

    @Test
    @DisplayName("Full tank to full tank with intermediate partial load")
    void fullTankWithIntermediatePartialLoad() {
        List<FullTankConsumptionCalculator.FuelLogEntry> entries = List.of(
                new FullTankConsumptionCalculator.FuelLogEntry(
                        LocalDate.of(2026, 1, 1),
                        10000,
                        FuelType.NAFTA_SUPER,
                        new BigDecimal("50.000"),
                        true),
                new FullTankConsumptionCalculator.FuelLogEntry(
                        LocalDate.of(2026, 1, 5),
                        10300,
                        FuelType.NAFTA_SUPER,
                        new BigDecimal("20.000"),
                        false),
                new FullTankConsumptionCalculator.FuelLogEntry(
                        LocalDate.of(2026, 1, 10),
                        10800,
                        FuelType.NAFTA_SUPER,
                        new BigDecimal("30.000"),
                        true));

        FullTankConsumptionCalculator.ConsumptionResult result =
                FullTankConsumptionCalculator.calculate(entries);

        // 800 km, 50 L consumed (20 + 30) -> 16 km/L, 6.25 L/100km
        assertThat(result.avgKmPerLiter()).isEqualByComparingTo(new BigDecimal("16.0000"));
        assertThat(result.avgLitersPer100Km()).isEqualByComparingTo(new BigDecimal("6.2500"));
    }

    @Test
    @DisplayName("GNC and ELECTRICO are excluded from liquid fuel consumption calculations")
    void gncAndElectricoExcluded() {
        List<FullTankConsumptionCalculator.FuelLogEntry> entries = List.of(
                new FullTankConsumptionCalculator.FuelLogEntry(
                        LocalDate.of(2026, 1, 1),
                        10000,
                        FuelType.GNC,
                        new BigDecimal("15.000"),
                        true),
                new FullTankConsumptionCalculator.FuelLogEntry(
                        LocalDate.of(2026, 1, 10),
                        10200,
                        FuelType.GNC,
                        new BigDecimal("14.000"),
                        true));

        FullTankConsumptionCalculator.ConsumptionResult result =
                FullTankConsumptionCalculator.calculate(entries);

        assertThat(result.avgKmPerLiter()).isNull();
        assertThat(result.avgLitersPer100Km()).isNull();
    }

    @Test
    @DisplayName("Without consecutive full tank pairs returns null")
    void noPairsReturnsNull() {
        List<FullTankConsumptionCalculator.FuelLogEntry> entries = List.of(
                new FullTankConsumptionCalculator.FuelLogEntry(
                        LocalDate.of(2026, 1, 1),
                        10000,
                        FuelType.NAFTA_SUPER,
                        new BigDecimal("40.000"),
                        true),
                new FullTankConsumptionCalculator.FuelLogEntry(
                        LocalDate.of(2026, 1, 10),
                        10500,
                        FuelType.NAFTA_SUPER,
                        new BigDecimal("20.000"),
                        false));

        FullTankConsumptionCalculator.ConsumptionResult result =
                FullTankConsumptionCalculator.calculate(entries);

        assertThat(result.avgKmPerLiter()).isNull();
        assertThat(result.avgLitersPer100Km()).isNull();
    }
}
