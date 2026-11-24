package com.familion.finance.exchanges.application;

import java.math.BigDecimal;

public record CurrencyPositionView(
        String currencyCode,
        BigDecimal units,
        BigDecimal weightedAvgRate,
        BigDecimal totalCostBase,
        BigDecimal currentRate,
        BigDecimal currentValueBase,
        BigDecimal unrealizedResultBase,
        BigDecimal unrealizedResultPct) {}
