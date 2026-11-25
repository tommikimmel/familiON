package com.familion.receipts.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemReceiptStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesUnderFamilyDirectoryWithRelativeKeyAndLoadsIdenticalBytes() {
        LocalFilesystemReceiptStorage storage = new LocalFilesystemReceiptStorage(tempDir);
        UUID familyId = UUID.randomUUID();
        byte[] bytes = new byte[] {1, 2, 3, 4, 5, 6};

        String key = storage.save(familyId, "png", bytes);

        assertThat(key).startsWith(familyId + "/").endsWith(".png");
        assertThat(Path.of(key).isAbsolute()).isFalse();
        assertThat(Files.isRegularFile(tempDir.resolve(key))).isTrue();
        assertThat(storage.load(key)).isEqualTo(bytes);
    }

    @Test
    void rejectsPathTraversalOnLoad() {
        LocalFilesystemReceiptStorage storage = new LocalFilesystemReceiptStorage(tempDir);

        assertThatThrownBy(() -> storage.load("../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsNotFoundWhenFileIsMissingOnDisk() {
        LocalFilesystemReceiptStorage storage = new LocalFilesystemReceiptStorage(tempDir);

        assertThatThrownBy(() -> storage.load(UUID.randomUUID() + "/missing.png"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteQuietlyRemovesStoredFile() {
        LocalFilesystemReceiptStorage storage = new LocalFilesystemReceiptStorage(tempDir);
        String key = storage.save(UUID.randomUUID(), "jpg", new byte[] {10, 20});

        storage.deleteQuietly(key);

        assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }
}
