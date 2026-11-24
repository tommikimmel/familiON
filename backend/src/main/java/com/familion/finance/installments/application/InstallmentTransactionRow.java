package com.familion.finance.installments.application;

import com.familion.finance.transactions.application.api.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentTransactionRow(
        UUID transactionId,
        UUID installmentPlanId,
        int installmentNumber,
        LocalDate transactionDate,
        BigDecimal amount,
        TransactionStatus status,
        UUID attributedToUserId,
        String attributedToFullName) {}
