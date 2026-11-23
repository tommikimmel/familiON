package com.familion.family.application;

public record UpdateFamilySettingsCommand(
        String name,
        String baseCurrency,
        String referenceCurrency,
        String referenceRateSource,
        String timezone) {}
