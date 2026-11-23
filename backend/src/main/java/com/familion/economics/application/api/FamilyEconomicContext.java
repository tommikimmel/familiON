package com.familion.economics.application.api;

public record FamilyEconomicContext(
        String baseCurrency, String referenceCurrency, String referenceRateSource) {}
