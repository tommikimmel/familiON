package com.familion.finance.exchanges.infrastructure.web;

import com.familion.finance.exchanges.application.CurrencyPositionView;
import java.math.BigDecimal;

public record CurrencyPositionResponse(
        String currencyCode,
        BigDecimal units,
        BigDecimal weightedAvgRate,
        BigDecimal totalCostBase,
        BigDecimal currentRate,
        BigDecimal currentValueBase,
        BigDecimal unrealizedResultBase,
        BigDecimal unrealizedResultPct) {

    public static CurrencyPositionResponse from(CurrencyPositionView view) {
        return new CurrencyPositionResponse(
                view.currencyCode(),
                view.units(),
                view.weightedAvgRate(),
                view.totalCostBase(),
                view.currentRate(),
                view.currentValueBase(),
                view.unrealizedResultBase(),
                view.unrealizedResultPct());
    }
}
