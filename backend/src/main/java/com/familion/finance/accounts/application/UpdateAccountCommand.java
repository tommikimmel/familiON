package com.familion.finance.accounts.application;

import com.familion.finance.accounts.domain.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateAccountCommand(
        String name,
        AccountType accountType,
        BigDecimal initialBalance,
        boolean ownerUserIdPresent,
        UUID ownerUserId,
        boolean creditLimitPresent,
        BigDecimal creditLimit,
        Integer closingDay,
        Integer dueDay) {}
