package com.familion.finance.exchanges.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExchangeCreateRequest(
        @NotNull(message = "Source account is required") UUID sourceAccountId,
        @NotNull(message = "Target account is required") UUID targetAccountId,
        @NotNull(message = "Source amount is required")
                @Positive(message = "Source amount must be greater than zero")
                BigDecimal sourceAmount,
        @NotNull(message = "Target amount is required")
                @Positive(message = "Target amount must be greater than zero")
                BigDecimal targetAmount,
        @Positive(message = "Applied exchange rate must be greater than zero")
                BigDecimal appliedExchangeRate,
        @PositiveOrZero(message = "Fee amount must be greater than or equal to zero")
                BigDecimal feeAmount,
        @Size(max = 10, message = "Fee currency must be at most 10 characters")
                String feeCurrency,
        LocalDate exchangeDate,
        @Size(max = 255, message = "Notes must be at most 255 characters") String notes) {}
