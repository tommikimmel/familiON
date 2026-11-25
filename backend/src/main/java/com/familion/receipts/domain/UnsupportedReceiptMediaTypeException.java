package com.familion.receipts.domain;

public class UnsupportedReceiptMediaTypeException extends RuntimeException {

    public UnsupportedReceiptMediaTypeException(String message) {
        super(message);
    }
}
