package com.familion.finance.transactions.application.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionChangedEvent(
        UUID familyId,
        UUID transactionId,
        BigDecimal amount,
        LocalDate date,
        boolean deleted) {}
