package com.familion.vehicles.infrastructure.web;

import com.familion.vehicles.domain.FuelType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record VehicleCreateRequest(
        @NotBlank @Size(max = 80) String nickname,
        @NotBlank @Size(max = 60) String brand,
        @NotBlank @Size(max = 80) String model,
        @NotNull @Min(1900) @Max(2100) Integer modelYear,
        @NotBlank @Size(max = 20) String licensePlate,
        @NotNull FuelType primaryFuelType,
        FuelType secondaryFuelType,
        @Min(0) Integer currentOdometerKm,
        LocalDate vtvExpirationDate,
        LocalDate insuranceExpiration,
        @Size(max = 100) String insuranceCompany,
        String notes) {}
