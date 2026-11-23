package com.familion.finance.accounts.infrastructure.web;

import com.familion.finance.accounts.domain.AccountType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record AccountUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "Account name must not be blank")
        @Size(max = 100, message = "Account name must be at most 100 characters")
        String name,
        AccountType accountType,
        BigDecimal initialBalance,
        Optional<UUID> ownerUserId,
        Optional<BigDecimal> creditLimit,
        Integer closingDay,
        Integer dueDay) {}
