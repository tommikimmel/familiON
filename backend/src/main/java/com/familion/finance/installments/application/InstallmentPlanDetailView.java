package com.familion.finance.installments.application;

import com.familion.finance.transactions.application.api.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InstallmentPlanDetailView(
        InstallmentPlanView plan, List<InstallmentItemView> installments) {

    public record InstallmentItemView(
            int number,
            LocalDate date,
            BigDecimal amount,
            TransactionStatus status,
            UUID transactionId) {}
}
