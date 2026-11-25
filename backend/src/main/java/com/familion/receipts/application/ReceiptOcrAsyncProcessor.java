package com.familion.receipts.application;

import com.familion.common.error.NotFoundException;
import com.familion.common.money.Money;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.receipts.application.port.ReceiptDraft;
import com.familion.receipts.application.port.ReceiptParserPort;
import com.familion.receipts.application.port.ReceiptStorage;
import com.familion.receipts.domain.OcrRawPayload;
import com.familion.receipts.domain.ReceiptAttachment;
import com.familion.receipts.infrastructure.config.ReceiptsConfig;
import com.familion.receipts.infrastructure.persistence.ReceiptAttachmentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReceiptOcrAsyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReceiptOcrAsyncProcessor.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final ReceiptAttachmentRepository repository;
    private final ReceiptStorage storage;
    private final ReceiptParserPort parserPort;
    private final CategoryQueryApi categoryQueryApi;
    private final TransactionTemplate transactionTemplate;
    private final Duration ocrTimeout;

    public ReceiptOcrAsyncProcessor(
            ReceiptAttachmentRepository repository,
            ReceiptStorage storage,
            ReceiptParserPort parserPort,
            CategoryQueryApi categoryQueryApi,
            TransactionTemplate transactionTemplate,
            @Value("${familion.receipts.ocr-timeout:PT30S}") Duration ocrTimeout) {
        this.repository = repository;
        this.storage = storage;
        this.parserPort = parserPort;
        this.categoryQueryApi = categoryQueryApi;
        this.transactionTemplate = transactionTemplate;
        this.ocrTimeout = ocrTimeout;
    }

    @Async(ReceiptsConfig.OCR_EXECUTOR_BEAN)
    public void processAsync(UUID receiptId) {
        ReceiptJobContext job = markProcessing(receiptId);
        if (job == null) {
            log.warn("Receipt {} not found when starting OCR processing", receiptId);
            return;
        }

        try {
            byte[] content = storage.load(job.storageKey());
            ReceiptDraft draft = parseWithTimeout(content, job.mimeType());
            OcrRawPayload payload = toPayload(draft);
            markParsed(receiptId, payload);
        } catch (TimeoutException ex) {
            String message = "OCR processing timed out after " + ocrTimeout.toSeconds() + "s";
            log.warn("Receipt {} OCR failed: {}", receiptId, message);
            markFailed(receiptId, message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Receipt {} OCR interrupted", receiptId);
            markFailed(receiptId, "OCR processing was interrupted");
        } catch (Exception ex) {
            String message = extractMessage(ex);
            log.warn("Receipt {} OCR failed: {}", receiptId, message);
            markFailed(receiptId, message);
        }
    }

    private ReceiptJobContext markProcessing(UUID receiptId) {
        return transactionTemplate.execute(status -> repository
                .findById(receiptId)
                .map(attachment -> {
                    attachment.markProcessing();
                    repository.saveAndFlush(attachment);
                    return new ReceiptJobContext(attachment.getStorageKey(), attachment.getMimeType());
                })
                .orElse(null));
    }

    private ReceiptDraft parseWithTimeout(byte[] content, String mimeType)
            throws InterruptedException, TimeoutException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ReceiptDraft> future = executor.submit(() -> parserPort.parse(content, mimeType));
            try {
                ReceiptDraft draft = future.get(ocrTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (draft == null) {
                    return new ReceiptDraft(null, null, null, null, null, BigDecimal.ZERO, null);
                }
                return draft;
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw ex;
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new RuntimeException(cause);
            }
        }
    }

    private OcrRawPayload toPayload(ReceiptDraft draft) {
        UUID suggestedCategoryId = resolveCategoryId(draft.suggestedCategoryKey());
        BigDecimal totalAmount =
                draft.totalAmount() != null ? Money.scale(draft.totalAmount()) : null;
        BigDecimal confidence = clampConfidence(draft.confidence());
        String isoDate = draft.date() != null ? draft.date().toString() : null;
        return new OcrRawPayload(
                draft.merchant(),
                isoDate,
                totalAmount,
                draft.currencyCode(),
                suggestedCategoryId,
                draft.suggestedCategoryKey(),
                confidence,
                draft.rawText(),
                null);
    }

    private UUID resolveCategoryId(String systemKey) {
        if (systemKey == null || systemKey.isBlank()) {
            return null;
        }
        try {
            return categoryQueryApi.requireSystemCategory(systemKey).id();
        } catch (NotFoundException ex) {
            log.warn("System category key '{}' not found during receipt OCR resolution", systemKey);
            return null;
        }
    }

    private void markParsed(UUID receiptId, OcrRawPayload payload) {
        transactionTemplate.executeWithoutResult(status -> repository
                .findById(receiptId)
                .ifPresent(attachment -> {
                    attachment.markParsed(payload);
                    repository.saveAndFlush(attachment);
                }));
    }

    private void markFailed(UUID receiptId, String reason) {
        transactionTemplate.executeWithoutResult(status -> repository
                .findById(receiptId)
                .ifPresent(attachment -> {
                    attachment.markFailed(truncate(reason));
                    repository.saveAndFlush(attachment);
                }));
    }

    private static BigDecimal clampConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return BigDecimal.ZERO;
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (confidence.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return confidence;
    }

    private static String extractMessage(Exception ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "OCR processing failed";
        }
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }

    private record ReceiptJobContext(String storageKey, String mimeType) {}
}
