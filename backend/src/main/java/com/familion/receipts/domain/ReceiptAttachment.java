package com.familion.receipts.domain;

import com.familion.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "receipt_attachments")
public class ReceiptAttachment extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false)
    private UUID uploadedByUserId;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", nullable = false, length = 20)
    private ReceiptOcrStatus ocrStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_raw_payload", columnDefinition = "jsonb")
    private OcrRawPayload ocrRawPayload;

    protected ReceiptAttachment() {}

    public ReceiptAttachment(
            UUID familyId,
            UUID uploadedByUserId,
            String storageKey,
            String mimeType,
            long fileSizeBytes) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.uploadedByUserId =
                Objects.requireNonNull(uploadedByUserId, "uploadedByUserId must not be null");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey must not be null");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("fileSizeBytes must be positive");
        }
        this.fileSizeBytes = fileSizeBytes;
        this.ocrStatus = ReceiptOcrStatus.UPLOADED;
    }

    public void markProcessing() {
        this.ocrStatus = ReceiptOcrStatus.PROCESSING;
    }

    public void markParsed(OcrRawPayload payload) {
        this.ocrStatus = ReceiptOcrStatus.PARSED;
        this.ocrRawPayload = Objects.requireNonNull(payload, "payload must not be null");
    }

    public void markFailed(String reason) {
        this.ocrStatus = ReceiptOcrStatus.FAILED;
        this.ocrRawPayload = OcrRawPayload.failure(reason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getUploadedByUserId() {
        return uploadedByUserId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public ReceiptOcrStatus getOcrStatus() {
        return ocrStatus;
    }

    public OcrRawPayload getOcrRawPayload() {
        return ocrRawPayload;
    }
}
