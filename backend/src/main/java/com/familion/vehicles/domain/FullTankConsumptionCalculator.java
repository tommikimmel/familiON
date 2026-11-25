package com.familion.vehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates average fuel consumption using the full-tank to full-tank method.
 *
 * <p>Note: Fuel types GNC (measured in m³) and ELECTRICO (measured in kWh) are
 * excluded from the average consumption calculations as their units are not
 * equivalent to liquid liters.
 */
public final class FullTankConsumptionCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private FullTankConsumptionCalculator() {}

    public static ConsumptionResult calculate(List<FuelLogEntry> logs) {
        if (logs == null || logs.isEmpty()) {
            return new ConsumptionResult(null, null);
        }

        List<FuelLogEntry> liquidLogs = logs.stream()
                .filter(l -> l.fuelType() != FuelType.GNC && l.fuelType() != FuelType.ELECTRICO)
                .sorted(Comparator.comparing(FuelLogEntry::fuelDate)
                        .thenComparingInt(FuelLogEntry::odometerKm))
                .toList();

        int firstFullIndex = -1;
        long totalKm = 0;
        BigDecimal totalLiters = BigDecimal.ZERO;
        boolean hasPair = false;

        for (int i = 0; i < liquidLogs.size(); i++) {
            if (liquidLogs.get(i).fullTank()) {
                if (firstFullIndex != -1) {
                    int km = liquidLogs.get(i).odometerKm() - liquidLogs.get(firstFullIndex).odometerKm();
                    if (km > 0) {
                        BigDecimal segmentLiters = BigDecimal.ZERO;
                        for (int k = firstFullIndex + 1; k <= i; k++) {
                            segmentLiters = segmentLiters.add(liquidLogs.get(k).liters());
                        }
                        if (segmentLiters.compareTo(BigDecimal.ZERO) > 0) {
                            totalKm += km;
                            totalLiters = totalLiters.add(segmentLiters);
                            hasPair = true;
                        }
                    }
                }
                firstFullIndex = i;
            }
        }

        if (!hasPair || totalKm <= 0 || totalLiters.compareTo(BigDecimal.ZERO) <= 0) {
            return new ConsumptionResult(null, null);
        }

        BigDecimal kmBig = BigDecimal.valueOf(totalKm);
        BigDecimal avgKmPerLiter = kmBig.divide(totalLiters, 4, RoundingMode.HALF_EVEN);
        BigDecimal avgLitersPer100Km = totalLiters.multiply(HUNDRED).divide(kmBig, 4, RoundingMode.HALF_EVEN);

        return new ConsumptionResult(avgKmPerLiter, avgLitersPer100Km);
    }

    public record FuelLogEntry(
            LocalDate fuelDate,
            int odometerKm,
            FuelType fuelType,
            BigDecimal liters,
            boolean fullTank) {}

    public record ConsumptionResult(
            BigDecimal avgKmPerLiter,
            BigDecimal avgLitersPer100Km) {}
}
