package com.familion.finance.accounts.infrastructure.web;

import com.familion.finance.accounts.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountCreateRequest(
        @NotBlank(message = "Account name is required")
        @Size(max = 100, message = "Account name must be at most 100 characters")
        String name,
        @NotNull(message = "Account type is required") AccountType accountType,
        @NotBlank(message = "Currency code is required")
        @Size(max = 10, message = "Currency code must be at most 10 characters")
        String currencyCode,
        BigDecimal initialBalance,
        UUID ownerUserId,
        BigDecimal creditLimit,
        Integer closingDay,
        Integer dueDay) {}
