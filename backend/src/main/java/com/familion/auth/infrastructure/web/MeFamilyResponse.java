package com.familion.auth.infrastructure.web;

import java.util.List;
import java.util.UUID;

public record MeFamilyResponse(
        UUID id,
        String name,
        String baseCurrency,
        String referenceCurrency,
        String referenceRateSource,
        String timezone,
        List<String> enabledCurrencies) {}
