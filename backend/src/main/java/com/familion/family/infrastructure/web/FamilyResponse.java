package com.familion.family.infrastructure.web;

import com.familion.family.application.FamilyView;
import java.util.List;
import java.util.UUID;

public record FamilyResponse(
        UUID id,
        String name,
        String baseCurrency,
        String referenceCurrency,
        String referenceRateSource,
        String timezone,
        List<FamilyCurrencyResponse> enabledCurrencies) {

    public static FamilyResponse from(FamilyView view) {
        return new FamilyResponse(
                view.id(),
                view.name(),
                view.baseCurrency(),
                view.referenceCurrency(),
                view.referenceRateSource(),
                view.timezone(),
                view.enabledCurrencies().stream().map(FamilyCurrencyResponse::from).toList());
    }
}
