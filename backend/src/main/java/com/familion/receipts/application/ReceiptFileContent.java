package com.familion.receipts.application;

public record ReceiptFileContent(byte[] content, String mimeType, String filename) {}
