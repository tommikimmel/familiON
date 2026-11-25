package com.familion.receipts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.receipts.application.port.ReceiptDraft;
import com.familion.receipts.application.port.ReceiptParserPort;
import com.familion.receipts.application.port.ReceiptStorage;
import com.familion.receipts.domain.ReceiptAttachment;
import com.familion.receipts.domain.ReceiptOcrStatus;
import com.familion.receipts.domain.ReceiptParsingException;
import com.familion.receipts.infrastructure.ocr.NoopReceiptParser;
import com.familion.receipts.infrastructure.persistence.ReceiptAttachmentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ReceiptOcrAsyncProcessorTest {

    private ReceiptAttachmentRepository repository;
    private ReceiptStorage storage;
    private ReceiptParserPort parserPort;
    private CategoryQueryApi categoryQueryApi;
    private TransactionTemplate transactionTemplate;

    private UUID receiptId;
    private ReceiptAttachment attachment;

    @BeforeEach
    void setUp() {
        repository = mock(ReceiptAttachmentRepository.class);
        storage = mock(ReceiptStorage.class);
        parserPort = mock(ReceiptParserPort.class);
        categoryQueryApi = mock(CategoryQueryApi.class);
        transactionTemplate = new ImmediateTransactionTemplate();

        receiptId = UUID.randomUUID();
        attachment = new ReceiptAttachment(
                UUID.randomUUID(), UUID.randomUUID(), "fam/rec.png", "image/png", 128);
        when(repository.findById(receiptId)).thenReturn(Optional.of(attachment));
        when(repository.saveAndFlush(any(ReceiptAttachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(storage.load("fam/rec.png")).thenReturn(new byte[] {1, 2, 3});
    }

    @Test
    void transitionsToParsedAndResolvesSystemCategoryId() {
        UUID categoryId = UUID.randomUUID();
        when(parserPort.parse(any(), any()))
                .thenReturn(new ReceiptDraft(
                        "COTO",
                        LocalDate.of(2026, 3, 14),
                        new BigDecimal("1234.56"),
                        "ARS",
                        "SUPERMARKET",
                        new BigDecimal("1.00"),
                        "COTO\nTOTAL 1234.56"));
        when(categoryQueryApi.requireSystemCategory("SUPERMARKET"))
                .thenReturn(new CategoryRef(categoryId, "Supermercado", "EXPENSE", "shopping-cart", null));

        ReceiptOcrAsyncProcessor processor = new ReceiptOcrAsyncProcessor(
                repository, storage, parserPort, categoryQueryApi, transactionTemplate, Duration.ofSeconds(5));

        processor.processAsync(receiptId);

        assertThat(attachment.getOcrStatus()).isEqualTo(ReceiptOcrStatus.PARSED);
        assertThat(attachment.getOcrRawPayload()).isNotNull();
        assertThat(attachment.getOcrRawPayload().merchant()).isEqualTo("COTO");
        assertThat(attachment.getOcrRawPayload().date()).isEqualTo("2026-03-14");
        assertThat(attachment.getOcrRawPayload().totalAmount())
                .isEqualByComparingTo(new BigDecimal("1234.5600"));
        assertThat(attachment.getOcrRawPayload().suggestedCategoryId()).isEqualTo(categoryId);
        assertThat(attachment.getOcrRawPayload().suggestedCategoryKey()).isEqualTo("SUPERMARKET");
        assertThat(attachment.getOcrRawPayload().confidence())
                .isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(attachment.getOcrRawPayload().error()).isNull();
    }

    @Test
    void noopParserProducesParsedEmptyDraftWithZeroConfidence() {
        ReceiptOcrAsyncProcessor processor = new ReceiptOcrAsyncProcessor(
                repository,
                storage,
                new NoopReceiptParser(),
                categoryQueryApi,
                transactionTemplate,
                Duration.ofSeconds(5));

        processor.processAsync(receiptId);

        assertThat(attachment.getOcrStatus()).isEqualTo(ReceiptOcrStatus.PARSED);
        assertThat(attachment.getOcrRawPayload()).isNotNull();
        assertThat(attachment.getOcrRawPayload().merchant()).isNull();
        assertThat(attachment.getOcrRawPayload().suggestedCategoryId()).isNull();
        assertThat(attachment.getOcrRawPayload().confidence()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(categoryQueryApi, never()).requireSystemCategory(any());
    }

    @Test
    void marksFailedWithReasonWhenParserThrows() {
        when(parserPort.parse(any(), any()))
                .thenThrow(new ReceiptParsingException("PDF OCR is not supported without pdftoppm"));

        ReceiptOcrAsyncProcessor processor = new ReceiptOcrAsyncProcessor(
                repository, storage, parserPort, categoryQueryApi, transactionTemplate, Duration.ofSeconds(5));

        processor.processAsync(receiptId);

        assertThat(attachment.getOcrStatus()).isEqualTo(ReceiptOcrStatus.FAILED);
        assertThat(attachment.getOcrRawPayload()).isNotNull();
        assertThat(attachment.getOcrRawPayload().error())
                .contains("PDF OCR is not supported without pdftoppm");
    }

    @Test
    void marksFailedWhenParserExceedsTimeout() {
        when(parserPort.parse(any(), any())).thenAnswer(inv -> {
            Thread.sleep(2000);
            return new ReceiptDraft(null, null, null, null, null, BigDecimal.ZERO, null);
        });

        ReceiptOcrAsyncProcessor processor = new ReceiptOcrAsyncProcessor(
                repository,
                storage,
                parserPort,
                categoryQueryApi,
                transactionTemplate,
                Duration.ofMillis(150));

        processor.processAsync(receiptId);

        assertThat(attachment.getOcrStatus()).isEqualTo(ReceiptOcrStatus.FAILED);
        assertThat(attachment.getOcrRawPayload().error()).contains("timed out");
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}
