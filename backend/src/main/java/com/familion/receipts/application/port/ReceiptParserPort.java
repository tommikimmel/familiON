package com.familion.receipts.application.port;

/**
 * Hexagonal port for extracting a structured {@link ReceiptDraft} from a receipt image or PDF.
 *
 * <p>Adapters are interchangeable and selected by availability/configuration (e.g. local CLI
 * Tesseract, a no-op fallback, or a future LLM Vision adapter without touching domain or
 * application code).
 */
public interface ReceiptParserPort {

    ReceiptDraft parse(byte[] content, String mimeType);
}
