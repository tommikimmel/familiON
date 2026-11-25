package com.familion.receipts.domain;

public enum ReceiptMediaType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    private final String mimeType;
    private final String extension;

    ReceiptMediaType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public static String extensionForMimeType(String mimeType) {
        for (ReceiptMediaType type : values()) {
            if (type.mimeType.equalsIgnoreCase(mimeType)) {
                return type.extension;
            }
        }
        return "bin";
    }
}
