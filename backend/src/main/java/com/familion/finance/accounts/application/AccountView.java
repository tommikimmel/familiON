package com.familion.finance.accounts.application;

import com.familion.common.money.Money;
import com.familion.finance.accounts.domain.Account;
import com.familion.finance.accounts.domain.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(
        UUID id,
        String name,
        AccountType accountType,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        UUID ownerUserId,
        BigDecimal creditLimit,
        Integer closingDay,
        Integer dueDay,
        boolean active) {

    public static AccountView from(Account account, BigDecimal currentBalance) {
        return new AccountView(
                account.getId(),
                account.getName(),
                account.getAccountType(),
                account.getCurrencyCode(),
                Money.scale(account.getInitialBalance()),
                Money.scale(currentBalance),
                account.getOwnerUserId(),
                account.getCreditLimit() == null ? null : Money.scale(account.getCreditLimit()),
                account.getClosingDay(),
                account.getDueDay(),
                account.isActive());
    }
}
