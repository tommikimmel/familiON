package com.familion.finance.installments.infrastructure.web;

import com.familion.finance.installments.domain.AmountMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentPlanCreateRequest(
        @NotBlank @Size(max = 200) String description,
        @NotNull UUID categoryId,
        @NotNull UUID creditCardAccountId,
        @NotBlank @Size(max = 10) String currencyCode,
        @NotNull @Min(2) @Max(60) Integer totalInstallments,
        @NotNull AmountMode amountMode,
        @NotNull @DecimalMin("0.0001") BigDecimal amount,
        boolean hasInterest,
        BigDecimal cashPrice,
        @NotNull LocalDate purchaseDate,
        UUID attributedToUserId) {}
