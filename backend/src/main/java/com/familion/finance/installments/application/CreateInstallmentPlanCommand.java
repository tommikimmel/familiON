package com.familion.finance.installments.application;

import com.familion.finance.installments.domain.AmountMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInstallmentPlanCommand(
        String description,
        UUID categoryId,
        UUID creditCardAccountId,
        String currencyCode,
        int totalInstallments,
        AmountMode amountMode,
        BigDecimal amount,
        boolean hasInterest,
        BigDecimal cashPrice,
        LocalDate purchaseDate,
        UUID attributedToUserId) {}
