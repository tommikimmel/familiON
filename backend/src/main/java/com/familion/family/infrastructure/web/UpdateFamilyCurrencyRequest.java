package com.familion.family.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

public record UpdateFamilyCurrencyRequest(
        @JsonProperty("isActive") Boolean isActive,
        @Pattern(
                regexp = "^(USD_OFICIAL|USD_MEP|USD_BLUE|EUR_OFICIAL|EUR_BLUE|UVA)$",
                message =
                        "Preferred rate source must be one of USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA")
        String preferredRateSource) {}
