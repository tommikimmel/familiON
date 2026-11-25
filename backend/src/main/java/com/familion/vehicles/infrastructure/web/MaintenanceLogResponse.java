package com.familion.vehicles.infrastructure.web;

import com.familion.vehicles.domain.MaintenanceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceLogResponse(
        UUID id,
        MaintenanceType maintenanceType,
        LocalDate maintenanceDate,
        int odometerKm,
        String description,
        String workshopName,
        BigDecimal costAmount,
        String currencyCode,
        UUID accountId,
        Integer nextDueKm,
        LocalDate nextDueDate,
        String notes,
        UUID transactionId,
        UserSummary user) {}
