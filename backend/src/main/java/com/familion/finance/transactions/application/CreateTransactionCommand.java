package com.familion.finance.transactions.application;

import com.familion.finance.transactions.application.api.Scope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionCommand(
        CreateTransactionType type,
        BigDecimal amount,
        String currencyCode,
        LocalDate transactionDate,
        UUID accountId,
        UUID targetAccountId,
        UUID categoryId,
        String description,
        String notes,
        Scope scope,
        UUID attributedToUserId,
        UUID receiptAttachmentId) {}
