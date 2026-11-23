package com.familion.finance.transactions.application;

import com.familion.finance.transactions.application.api.Scope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionPatchCommand(
        BigDecimal amount,
        LocalDate transactionDate,
        boolean categoryIdPresent,
        UUID categoryId,
        boolean descriptionPresent,
        String description,
        boolean notesPresent,
        String notes,
        UUID accountId,
        UUID attributedToUserId,
        Scope scope) {}
