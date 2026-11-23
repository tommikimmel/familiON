package com.familion.family.infrastructure.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateFamilySettingsRequest(
        @Pattern(regexp = ".*\\S.*", message = "Family name must not be blank")
        @Size(max = 120, message = "Family name must be at most 120 characters")
        String name,
        @Pattern(
                regexp = "^(ARS|USD|EUR|USDT)$",
                message = "Base currency must be one of ARS, USD, EUR, USDT")
        String baseCurrency,
        @Pattern(
                regexp = "^(USD|EUR|UVA|ARS)$",
                message = "Reference currency must be one of USD, EUR, UVA, ARS")
        String referenceCurrency,
        @Pattern(
                regexp = "^(USD_OFICIAL|USD_MEP|USD_BLUE|EUR_OFICIAL|EUR_BLUE|UVA)$",
                message =
                        "Reference rate source must be one of USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA")
        String referenceRateSource,
        @ValidZoneId String timezone) {}
