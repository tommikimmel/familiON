package com.familion.finance.exchanges.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateExchangeCommand(
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal sourceAmount,
        BigDecimal targetAmount,
        BigDecimal appliedExchangeRate,
        BigDecimal feeAmount,
        String feeCurrency,
        LocalDate exchangeDate,
        String notes) {}
