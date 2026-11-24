package com.familion.finance.installments.application;

import com.familion.finance.installments.domain.InstallmentPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentPlanReadRow(
        UUID id,
        UUID familyId,
        UUID userId,
        String description,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColorHex,
        UUID creditCardAccountId,
        String creditCardAccountName,
        Integer creditCardClosingDay,
        Integer creditCardDueDay,
        String currencyCode,
        int totalInstallments,
        boolean hasInterest,
        BigDecimal cashPrice,
        BigDecimal financedTotal,
        BigDecimal installmentAmount,
        LocalDate purchaseDate,
        LocalDate firstBillingDate,
        InstallmentPlanStatus status,
        String creatorFullName) {}
