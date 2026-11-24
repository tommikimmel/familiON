package com.familion.finance.installments.application;

import com.familion.finance.installments.domain.InstallmentPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentPlanView(
        UUID id,
        String description,
        CategorySummary category,
        AccountSummary creditCardAccount,
        String currencyCode,
        int totalInstallments,
        int paidInstallments,
        boolean hasInterest,
        BigDecimal cashPrice,
        BigDecimal financedTotal,
        BigDecimal installmentAmount,
        BigDecimal surchargeAmount,
        BigDecimal surchargePct,
        LocalDate purchaseDate,
        LocalDate firstBillingDate,
        LocalDate lastBillingDate,
        BigDecimal remainingAmount,
        BigDecimal realRemainingAmount,
        NextInstallmentSummary nextInstallment,
        InstallmentPlanStatus status,
        MemberSummary attributedTo) {

    public record CategorySummary(UUID id, String name, String icon, String colorHex) {}

    public record AccountSummary(UUID id, String name) {}

    public record NextInstallmentSummary(int number, LocalDate date, BigDecimal amount) {}

    public record MemberSummary(UUID id, String fullName) {}
}
