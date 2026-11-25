package com.familion.vehicles.infrastructure.web;

import com.familion.vehicles.domain.MaintenanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceLogCreateRequest(
        @NotNull MaintenanceType maintenanceType,
        LocalDate maintenanceDate,
        @NotNull @Min(0) Integer odometerKm,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 120) String workshopName,
        @NotNull @DecimalMin("0.0") BigDecimal costAmount,
        @NotBlank @Size(max = 10) String currencyCode,
        UUID accountId,
        Integer nextDueKm,
        LocalDate nextDueDate,
        String notes) {}
