package com.familion.vehicles.application.api;

import java.time.LocalDate;
import java.util.UUID;

public record VehicleAlertRef(
        UUID familyId,
        UUID vehicleId,
        String vehicleNickname,
        String kind,
        String title,
        LocalDate dueDate,
        Integer dueKm,
        String severity) {}
