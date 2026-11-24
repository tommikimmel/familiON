package com.familion.finance.exchanges.application;

import com.familion.finance.exchanges.domain.ExchangeOperation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExchangeView(
        UUID id,
        LocalDate exchangeDate,
        AccountSummary sourceAccount,
        AccountSummary targetAccount,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal appliedExchangeRate,
        BigDecimal feeAmount,
        String feeCurrency,
        String notes,
        ExchangeOperation operation,
        UserSummary createdBy) {

    public record AccountSummary(UUID id, String name) {}

    public record UserSummary(UUID id, String fullName) {}
}
