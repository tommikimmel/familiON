package com.familion.receipts.domain;

public class EmptyReceiptFileException extends RuntimeException {

    public EmptyReceiptFileException(String message) {
        super(message);
    }
}
