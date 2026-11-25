package com.familion.receipts.infrastructure.web;

import com.familion.receipts.domain.ReceiptAttachment;
import com.familion.receipts.domain.ReceiptOcrStatus;
import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        ReceiptOcrStatus status,
        String mimeType,
        long fileSizeBytes,
        Instant createdAt,
        ReceiptDraftResponse draft,
        String fileUrl) {

    public static ReceiptResponse from(ReceiptAttachment attachment) {
        ReceiptDraftResponse draft = attachment.getOcrStatus() == ReceiptOcrStatus.PARSED
                        && attachment.getOcrRawPayload() != null
                ? ReceiptDraftResponse.from(attachment.getOcrRawPayload())
                : null;
        return new ReceiptResponse(
                attachment.getId(),
                attachment.getOcrStatus(),
                attachment.getMimeType(),
                attachment.getFileSizeBytes(),
                attachment.getCreatedAt(),
                draft,
                "/api/v1/receipts/" + attachment.getId() + "/file");
    }
}
