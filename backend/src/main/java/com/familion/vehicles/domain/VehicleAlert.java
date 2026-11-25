package com.familion.vehicles.domain;

import java.time.LocalDate;

public record VehicleAlert(
        String kind,
        String title,
        LocalDate dueDate,
        Integer dueKm,
        String severity) {}
