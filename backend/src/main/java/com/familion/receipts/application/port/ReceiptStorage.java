package com.familion.receipts.application.port;

import java.util.UUID;

public interface ReceiptStorage {

    String save(UUID familyId, String extension, byte[] content);

    byte[] load(String storageKey);

    void deleteQuietly(String storageKey);
}
