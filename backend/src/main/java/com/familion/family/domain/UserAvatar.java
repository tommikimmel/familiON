package com.familion.family.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    public static final int MAX_SIZE_BYTES = 2 * 1024 * 1024;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50};

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAvatar() {}

    public UserAvatar(UUID userId) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
    }

    public void replace(String contentType, byte[] content, Instant updatedAt) {
        this.contentType = contentType;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    /** Detects the image type from magic bytes; the client-declared type is never trusted. */
    public static Optional<String> detectContentType(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, 0, JPEG_MAGIC)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(content, 0, PNG_MAGIC)) {
            return Optional.of("image/png");
        }
        if (startsWith(content, 0, RIFF_MAGIC) && startsWith(content, 8, WEBP_MAGIC)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] magic) {
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

    public UUID getUserId() {
        return userId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
