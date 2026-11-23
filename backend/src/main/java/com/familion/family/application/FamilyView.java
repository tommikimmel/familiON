package com.familion.family.application;

import java.util.List;
import java.util.UUID;

public record FamilyView(
        UUID id,
        String name,
        String baseCurrency,
        String referenceCurrency,
        String referenceRateSource,
        String timezone,
        List<FamilyCurrencyView> enabledCurrencies) {}
