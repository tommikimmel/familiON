package com.familion.finance.accounts.infrastructure.web;

import com.familion.finance.accounts.application.AccountView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String accountType,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        UUID ownerUserId,
        BigDecimal creditLimit,
        Integer closingDay,
        Integer dueDay,
        @JsonProperty("isActive") boolean isActive) {

    public static AccountResponse from(AccountView view) {
        return new AccountResponse(
                view.id(),
                view.name(),
                view.accountType().name(),
                view.currencyCode(),
                view.initialBalance(),
                view.currentBalance(),
                view.ownerUserId(),
                view.creditLimit(),
                view.closingDay(),
                view.dueDay(),
                view.active());
    }
}
