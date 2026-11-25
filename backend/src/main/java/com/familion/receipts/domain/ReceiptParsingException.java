package com.familion.receipts.domain;

public class ReceiptParsingException extends RuntimeException {

    public ReceiptParsingException(String message) {
        super(message);
    }

    public ReceiptParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
