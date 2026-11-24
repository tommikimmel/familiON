package com.familion.finance.exchanges.application;

import java.math.BigDecimal;

public record ExchangePositionRow(
        String sourceCurrency,
        BigDecimal sourceAmount,
        String targetCurrency,
        BigDecimal targetAmount) {}
