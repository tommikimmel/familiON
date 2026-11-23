package com.familion.family.infrastructure.web;

import com.familion.family.application.FamilyCurrencyView;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FamilyCurrencyResponse(
        String currencyCode,
        String preferredRateSource,
        @JsonProperty("isActive") boolean isActive) {

    public static FamilyCurrencyResponse from(FamilyCurrencyView view) {
        return new FamilyCurrencyResponse(
                view.currencyCode(), view.preferredRateSource(), view.active());
    }
}
