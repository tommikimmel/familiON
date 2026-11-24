package com.familion.finance.recurring.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringPaymentRow(
        UUID recurringExpenseId,
        UUID transactionId,
        BigDecimal amount,
        LocalDate transactionDate) {}
