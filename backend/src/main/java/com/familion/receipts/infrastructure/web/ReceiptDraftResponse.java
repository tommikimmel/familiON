package com.familion.receipts.infrastructure.web;

import com.familion.receipts.domain.OcrRawPayload;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceiptDraftResponse(
        String merchant,
        LocalDate date,
        BigDecimal totalAmount,
        String currencyCode,
        UUID suggestedCategoryId,
        String suggestedCategoryKey,
        BigDecimal confidence,
        String rawText) {

    public static ReceiptDraftResponse from(OcrRawPayload payload) {
        LocalDate parsedDate = payload.date() != null && !payload.date().isBlank()
                ? LocalDate.parse(payload.date())
                : null;
        return new ReceiptDraftResponse(
                payload.merchant(),
                parsedDate,
                payload.totalAmount(),
                payload.currencyCode(),
                payload.suggestedCategoryId(),
                payload.suggestedCategoryKey(),
                payload.confidence(),
                payload.rawText());
    }
}
