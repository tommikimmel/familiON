package com.familion.vehicles.infrastructure.web;

import com.familion.vehicles.domain.FuelType;
import com.familion.vehicles.domain.VehicleAlert;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String nickname,
        String brand,
        String model,
        int modelYear,
        String licensePlate,
        FuelType primaryFuelType,
        FuelType secondaryFuelType,
        int currentOdometerKm,
        LocalDate vtvExpirationDate,
        LocalDate insuranceExpiration,
        String insuranceCompany,
        String notes,
        boolean isActive,
        List<VehicleAlert> alerts) {}
