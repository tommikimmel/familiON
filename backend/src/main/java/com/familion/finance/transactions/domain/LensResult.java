package com.familion.finance.transactions.domain;

import java.math.BigDecimal;

public record LensResult(
        Lens lens, BigDecimal lensAmount, String lensUnit, boolean lensApproximated) {}
