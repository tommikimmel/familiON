package com.familion.finance.exchanges.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExchangeReadRow(
        UUID id,
        LocalDate exchangeDate,
        UUID sourceAccountId,
        String sourceAccountName,
        UUID targetAccountId,
        String targetAccountName,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal appliedExchangeRate,
        BigDecimal feeAmount,
        String feeCurrency,
        String notes,
        UUID createdById,
        String createdByFullName) {}
