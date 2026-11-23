package com.familion.finance.transactions.infrastructure.web;

import com.familion.finance.transactions.application.CreateTransactionType;
import com.familion.finance.transactions.application.api.Scope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionCreateRequest(
        @NotNull(message = "Transaction type is required") CreateTransactionType type,
        @NotNull(message = "Amount is required")
                @Positive(message = "Amount must be greater than zero")
                BigDecimal amount,
        @NotBlank(message = "Currency code is required")
                @Size(max = 10, message = "Currency code must be at most 10 characters")
                String currencyCode,
        LocalDate transactionDate,
        @NotNull(message = "Account is required") UUID accountId,
        UUID targetAccountId,
        UUID categoryId,
        @Size(max = 255, message = "Description must be at most 255 characters")
                String description,
        String notes,
        Scope scope,
        UUID attributedToUserId,
        UUID receiptAttachmentId) {}
