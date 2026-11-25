package com.familion.receipts.domain;

public class ReceiptTooLargeException extends RuntimeException {

    public ReceiptTooLargeException(String message) {
        super(message);
    }
}
