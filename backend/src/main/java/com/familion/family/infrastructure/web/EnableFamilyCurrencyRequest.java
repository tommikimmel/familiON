package com.familion.family.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EnableFamilyCurrencyRequest(
        @NotBlank(message = "Currency code is required")
        @Pattern(
                regexp = "^(ARS|USD|EUR|USDT)$",
                message = "Currency code must be one of ARS, USD, EUR, USDT")
        String currencyCode,
        @Pattern(
                regexp = "^(USD_OFICIAL|USD_MEP|USD_BLUE|EUR_OFICIAL|EUR_BLUE|UVA)$",
                message =
                        "Preferred rate source must be one of USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA")
        String preferredRateSource) {}
