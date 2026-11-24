package com.familion.finance.recurring.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record ConfirmPaymentRequest(
        @NotNull(message = "Month is required") YearMonth month,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,
        UUID accountId,
        LocalDate paymentDate) {}
