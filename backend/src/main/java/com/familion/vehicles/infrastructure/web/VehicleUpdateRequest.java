package com.familion.vehicles.infrastructure.web;

import com.familion.vehicles.domain.FuelType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record VehicleUpdateRequest(
        @Size(max = 80) String nickname,
        @Size(max = 60) String brand,
        @Size(max = 80) String model,
        @Min(1900) @Max(2100) Integer modelYear,
        @Size(max = 20) String licensePlate,
        FuelType primaryFuelType,
        FuelType secondaryFuelType,
        @Min(0) Integer currentOdometerKm,
        LocalDate vtvExpirationDate,
        LocalDate insuranceExpiration,
        @Size(max = 100) String insuranceCompany,
        String notes) {}
