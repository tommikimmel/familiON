package com.familion.receipts.infrastructure.ocr;

import com.familion.receipts.application.port.ReceiptDraft;
import com.familion.receipts.application.port.ReceiptParserPort;
import java.math.BigDecimal;

public class NoopReceiptParser implements ReceiptParserPort {

    @Override
    public ReceiptDraft parse(byte[] content, String mimeType) {
        return new ReceiptDraft(null, null, null, null, null, BigDecimal.ZERO, null);
    }
}
