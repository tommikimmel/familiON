package com.familion.economics.application.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EconomicSnapshotData(
        String referenceCurrency,
        String referenceRateSource,
        BigDecimal referenceExchangeRate,
        BigDecimal amountInReference,
        BigDecimal baseCurrencyAmount,
        BigDecimal ipcIndexValue,
        BigDecimal uvaValue,
        LocalDate snapshotDate) {}
