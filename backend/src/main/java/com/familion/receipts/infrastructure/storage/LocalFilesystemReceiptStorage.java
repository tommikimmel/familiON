package com.familion.receipts.infrastructure.storage;

import com.familion.common.error.NotFoundException;
import com.familion.receipts.application.port.ReceiptStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalFilesystemReceiptStorage implements ReceiptStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemReceiptStorage.class);

    private final Path baseDir;

    public LocalFilesystemReceiptStorage(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String save(UUID familyId, String extension, byte[] content) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(extension, "extension must not be null");
        Objects.requireNonNull(content, "content must not be null");

        String sanitizedExt = extension.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (sanitizedExt.isEmpty()) {
            throw new IllegalArgumentException("extension must not be blank");
        }
        String storageKey = familyId + "/" + UUID.randomUUID() + "." + sanitizedExt;
        Path target = resolveSafe(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return storageKey;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write receipt file " + storageKey, ex);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new NotFoundException("Receipt file not found");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read receipt file " + storageKey, ex);
        }
    }

    @Override
    public void deleteQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Path target = resolveSafe(storageKey);
            Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("Failed to clean up receipt file {}: {}", storageKey, ex.getMessage());
        }
    }

    private Path resolveSafe(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        Path resolved = baseDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid storage key outside base directory");
        }
        return resolved;
    }
}
