package com.familion.analytics.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PersonAggregateRow(
        UUID userId,
        String fullName,
        String accentColor,
        BigDecimal lensAmount,
        BigDecimal nominalAmount) {}
