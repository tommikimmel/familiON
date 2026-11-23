package com.familion.finance.transactions.application.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseAttributedEvent(
        UUID familyId,
        UUID transactionId,
        UUID paidByUserId,
        UUID attributedToUserId,
        BigDecimal amount,
        String currencyCode) {}
