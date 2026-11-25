package com.familion.receipts.domain;

import java.util.Optional;

public final class ReceiptMediaTypeDetector {

    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46}; // RIFF
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50}; // WEBP

    private ReceiptMediaTypeDetector() {}

    public static Optional<ReceiptMediaType> detect(byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        if (matchesAt(content, 0, JPEG_MAGIC)) {
            return Optional.of(ReceiptMediaType.JPEG);
        }
        if (matchesAt(content, 0, PNG_MAGIC)) {
            return Optional.of(ReceiptMediaType.PNG);
        }
        if (content.length >= 12
                && matchesAt(content, 0, RIFF_MAGIC)
                && matchesAt(content, 8, WEBP_MAGIC)) {
            return Optional.of(ReceiptMediaType.WEBP);
        }
        if (matchesAt(content, 0, PDF_MAGIC)) {
            return Optional.of(ReceiptMediaType.PDF);
        }
        return Optional.empty();
    }

    private static boolean matchesAt(byte[] content, int offset, byte[] magic) {
        if (content.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
