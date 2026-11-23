package com.familion.family.application;

public record FamilyCurrencyView(
        String currencyCode, String preferredRateSource, boolean active) {}
