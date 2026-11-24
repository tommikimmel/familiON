package com.familion.analytics.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryAggregateRow(
        UUID categoryId,
        String name,
        String colorHex,
        String icon,
        BigDecimal lensAmount,
        BigDecimal nominalAmount,
        BigDecimal constantBaseAmount) {}
