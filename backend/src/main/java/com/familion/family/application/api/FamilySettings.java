package com.familion.family.application.api;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record FamilySettings(
        UUID familyId,
        String name,
        String baseCurrency,
        String referenceCurrency,
        String referenceRateSource,
        ZoneId timezone,
        List<String> enabledCurrencies) {}
