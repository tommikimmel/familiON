package com.familion.finance.exchanges.infrastructure.web;

import com.familion.finance.exchanges.application.ExchangeView;
import com.familion.finance.exchanges.domain.ExchangeOperation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExchangeResponse(
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

    public static ExchangeResponse from(ExchangeView view) {
        return new ExchangeResponse(
                view.id(),
                view.exchangeDate(),
                new AccountSummary(view.sourceAccount().id(), view.sourceAccount().name()),
                new AccountSummary(view.targetAccount().id(), view.targetAccount().name()),
                view.sourceAmount(),
                view.sourceCurrency(),
                view.targetAmount(),
                view.targetCurrency(),
                view.appliedExchangeRate(),
                view.feeAmount(),
                view.feeCurrency(),
                view.notes(),
                view.operation(),
                new UserSummary(view.createdBy().id(), view.createdBy().fullName()));
    }
}
