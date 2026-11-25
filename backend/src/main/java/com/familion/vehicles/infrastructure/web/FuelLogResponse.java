package com.familion.vehicles.infrastructure.web;

import com.familion.vehicles.domain.FuelType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FuelLogResponse(
        UUID id,
        LocalDate fuelDate,
        int odometerKm,
        FuelType fuelType,
        BigDecimal liters,
        BigDecimal pricePerLiter,
        BigDecimal totalAmount,
        String currencyCode,
        boolean fullTank,
        String stationName,
        String notes,
        UUID transactionId,
        UserSummary user) {}
