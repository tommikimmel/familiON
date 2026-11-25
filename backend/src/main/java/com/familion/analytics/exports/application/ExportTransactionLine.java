package com.familion.analytics.exports.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExportTransactionLine(
        LocalDate date,
        String typeLabel,
        String description,
        String categoryName,
        String accountName,
        String paidByName,
        String attributedToName,
        String currencyCode,
        BigDecimal amount,
        BigDecimal lensAmount,
        String installmentLabel,
        String statusLabel) {}
