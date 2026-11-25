package com.familion.receipts.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptDraft(
        String merchant,
        LocalDate date,
        BigDecimal totalAmount,
        String currencyCode,
        String suggestedCategoryKey,
        BigDecimal confidence,
        String rawText) {}
