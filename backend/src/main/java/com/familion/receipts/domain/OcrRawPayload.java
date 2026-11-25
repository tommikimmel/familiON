package com.familion.receipts.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OcrRawPayload(
        String merchant,
        String date,
        BigDecimal totalAmount,
        String currencyCode,
        UUID suggestedCategoryId,
        String suggestedCategoryKey,
        BigDecimal confidence,
        String rawText,
        String error) {

    public static OcrRawPayload failure(String error) {
        return new OcrRawPayload(null, null, null, null, null, null, null, null, error);
    }
}
