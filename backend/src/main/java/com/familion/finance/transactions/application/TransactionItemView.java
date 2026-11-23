package com.familion.finance.transactions.application;

import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Lens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionItemView(
        UUID id,
        TransactionType type,
        Scope scope,
        TransactionStatus status,
        BigDecimal amount,
        String currencyCode,
        LocalDate transactionDate,
        String description,
        String notes,
        CategorySummary category,
        AccountSummary account,
        UserSummary paidBy,
        UserSummary attributedTo,
        InstallmentSummary installment,
        Lens lens,
        BigDecimal lensAmount,
        String lensUnit,
        boolean lensApproximated) {

    public record CategorySummary(UUID id, String name, String icon, String colorHex) {}

    public record AccountSummary(UUID id, String name, String accountType) {}

    public record UserSummary(UUID id, String fullName) {}

    public record InstallmentSummary(UUID planId, Integer number, Integer total) {}
}
