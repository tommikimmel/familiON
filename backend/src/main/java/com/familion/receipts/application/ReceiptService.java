package com.familion.receipts.application;

import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.CurrentUserProvider;
import com.familion.receipts.application.port.ReceiptStorage;
import com.familion.receipts.domain.EmptyReceiptFileException;
import com.familion.receipts.domain.ReceiptAttachment;
import com.familion.receipts.domain.ReceiptMediaType;
import com.familion.receipts.domain.ReceiptMediaTypeDetector;
import com.familion.receipts.domain.ReceiptTooLargeException;
import com.familion.receipts.domain.UnsupportedReceiptMediaTypeException;
import com.familion.receipts.infrastructure.persistence.ReceiptAttachmentRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReceiptService {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;

    private final CurrentUserProvider currentUserProvider;
    private final ReceiptAttachmentRepository repository;
    private final ReceiptStorage storage;
    private final TransactionTemplate transactionTemplate;
    private final ReceiptOcrAsyncProcessor ocrAsyncProcessor;

    public ReceiptService(
            CurrentUserProvider currentUserProvider,
            ReceiptAttachmentRepository repository,
            ReceiptStorage storage,
            TransactionTemplate transactionTemplate,
            ReceiptOcrAsyncProcessor ocrAsyncProcessor) {
        this.currentUserProvider = currentUserProvider;
        this.repository = repository;
        this.storage = storage;
        this.transactionTemplate = transactionTemplate;
        this.ocrAsyncProcessor = ocrAsyncProcessor;
    }

    public ReceiptAttachment upload(byte[] content) {
        validateSize(content);
        ReceiptMediaType mediaType = ReceiptMediaTypeDetector.detect(content)
                .orElseThrow(() -> new UnsupportedReceiptMediaTypeException(
                        "Unsupported receipt file type. Allowed formats: JPEG, PNG, WebP, PDF"));

        CurrentUser currentUser = currentUserProvider.get();
        String storageKey = storage.save(currentUser.familyId(), mediaType.extension(), content);
        ReceiptAttachment saved;
        try {
            saved = transactionTemplate.execute(status -> {
                ReceiptAttachment attachment = new ReceiptAttachment(
                        currentUser.familyId(),
                        currentUser.userId(),
                        storageKey,
                        mediaType.mimeType(),
                        content.length);
                return repository.saveAndFlush(attachment);
            });
            saved = Objects.requireNonNull(saved, "Saved receipt attachment must not be null");
        } catch (RuntimeException ex) {
            storage.deleteQuietly(storageKey);
            throw ex;
        }
        ocrAsyncProcessor.processAsync(saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public ReceiptAttachment getById(UUID receiptId) {
        return findAccessible(receiptId);
    }

    @Transactional(readOnly = true)
    public ReceiptFileContent getFile(UUID receiptId) {
        ReceiptAttachment attachment = findAccessible(receiptId);
        byte[] content = storage.load(attachment.getStorageKey());
        String extension = ReceiptMediaType.extensionForMimeType(attachment.getMimeType());
        return new ReceiptFileContent(
                content, attachment.getMimeType(), attachment.getId() + "." + extension);
    }

    private ReceiptAttachment findAccessible(UUID receiptId) {
        CurrentUser currentUser = currentUserProvider.get();
        ReceiptAttachment attachment = repository
                .findByIdAndFamilyId(receiptId, currentUser.familyId())
                .orElseThrow(() -> new NotFoundException("Receipt not found"));
        if (!currentUser.isAdult()
                && !attachment.getUploadedByUserId().equals(currentUser.userId())) {
            throw new NotFoundException("Receipt not found");
        }
        return attachment;
    }

    private static void validateSize(byte[] content) {
        if (content == null || content.length == 0) {
            throw new EmptyReceiptFileException("Receipt file must not be empty");
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new ReceiptTooLargeException(
                    "Receipt file exceeds the maximum allowed size of 5 MB");
        }
    }
}
