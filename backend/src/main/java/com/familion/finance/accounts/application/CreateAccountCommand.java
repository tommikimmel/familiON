package com.familion.finance.accounts.application;

import com.familion.finance.accounts.domain.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountCommand(
        String name,
        AccountType accountType,
        String currencyCode,
        BigDecimal initialBalance,
        UUID ownerUserId,
        BigDecimal creditLimit,
        Integer closingDay,
        Integer dueDay) {}
