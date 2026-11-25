package com.familion.analytics.exports.application;

import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExportTransactionRow(
        LocalDate transactionDate,
        TransactionType type,
        String description,
        String categoryName,
        String accountName,
        String paidByFullName,
        String attributedToFullName,
        String currencyCode,
        BigDecimal amount,
        BigDecimal snapshotBaseCurrencyAmount,
        BigDecimal snapshotAmountInReference,
        BigDecimal snapshotIpcIndexValue,
        Integer installmentNumber,
        Integer totalInstallments,
        TransactionStatus status) {}
