package com.familion.vehicles.infrastructure.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VehicleSummaryResponse(
        UUID vehicleId,
        LocalDate from,
        LocalDate to,
        String lens,
        String unit,
        int distanceKm,
        BigDecimal totalLiters,
        BigDecimal avgKmPerLiter,
        BigDecimal avgLitersPer100Km,
        BigDecimal costPerKm,
        BigDecimal totalCost,
        BigDecimal fuelCost,
        BigDecimal maintenanceCost,
        boolean lensApproximated,
        List<MonthlySummary> monthly,
        List<PriceTrendPoint> pricePerLiterTrend) {

    public record MonthlySummary(
            String month,
            BigDecimal fuelCost,
            BigDecimal maintenanceCost,
            int km) {}

    public record PriceTrendPoint(
            String date,
            BigDecimal pricePerLiter,
            String fuelType) {}
}
