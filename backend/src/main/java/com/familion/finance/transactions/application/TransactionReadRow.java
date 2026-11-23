package com.familion.finance.transactions.application;

import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionReadRow(
        UUID id,
        TransactionType type,
        Scope scope,
        TransactionStatus status,
        BigDecimal amount,
        String currencyCode,
        LocalDate transactionDate,
        String description,
        String notes,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColorHex,
        UUID accountId,
        String accountName,
        String accountType,
        UUID paidById,
        String paidByFullName,
        UUID attributedToId,
        String attributedToFullName,
        UUID installmentPlanId,
        Integer installmentNumber,
        Integer installmentTotal,
        String snapshotReferenceCurrency,
        BigDecimal snapshotAmountInReference,
        BigDecimal snapshotBaseCurrencyAmount,
        BigDecimal snapshotIpcIndexValue) {}
