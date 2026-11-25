package com.familion.vehicles.infrastructure.web;

import com.familion.vehicles.domain.FuelType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FuelLogCreateRequest(
        LocalDate fuelDate,
        @NotNull @Min(0) Integer odometerKm,
        @NotNull FuelType fuelType,
        BigDecimal liters,
        BigDecimal pricePerLiter,
        BigDecimal totalAmount,
        @NotBlank @Size(max = 10) String currencyCode,
        Boolean fullTank,
        @Size(max = 100) String stationName,
        @Size(max = 255) String notes,
        @NotNull UUID accountId,
        UUID attributedToUserId) {}
