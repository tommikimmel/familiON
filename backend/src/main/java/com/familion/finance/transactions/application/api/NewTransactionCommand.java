package com.familion.finance.transactions.application.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record NewTransactionCommand(
        UUID familyId,
        UUID userId,
        UUID attributedToUserId,
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        Scope scope,
        TransactionStatus status,
        BigDecimal amount,
        String currencyCode,
        LocalDate transactionDate,
        String description,
        String notes,
        UUID installmentPlanId,
        Integer installmentNumber,
        UUID recurringExpenseId,
        UUID currencyExchangeId,
        UUID allowanceId,
        UUID receiptAttachmentId,
        UUID transferGroupId) {}
