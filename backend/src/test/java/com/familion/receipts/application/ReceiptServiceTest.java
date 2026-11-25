package com.familion.receipts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.receipts.application.port.ReceiptStorage;
import com.familion.receipts.domain.EmptyReceiptFileException;
import com.familion.receipts.domain.ReceiptAttachment;
import com.familion.receipts.domain.ReceiptOcrStatus;
import com.familion.receipts.domain.ReceiptTooLargeException;
import com.familion.receipts.domain.UnsupportedReceiptMediaTypeException;
import com.familion.receipts.infrastructure.persistence.ReceiptAttachmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ReceiptServiceTest {

    private static final byte[] PNG_BYTES = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    };

    private CurrentUserProvider currentUserProvider;
    private ReceiptAttachmentRepository repository;
    private ReceiptStorage storage;
    private TransactionTemplate transactionTemplate;
    private ReceiptService service;

    private UUID familyId;
    private UUID ownerId;
    private UUID adultId;
    private UUID otherChildId;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        repository = mock(ReceiptAttachmentRepository.class);
        storage = mock(ReceiptStorage.class);
        transactionTemplate = new ImmediateTransactionTemplate();
        service = new ReceiptService(currentUserProvider, repository, storage, transactionTemplate);

        familyId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adultId = UUID.randomUUID();
        otherChildId = UUID.randomUUID();
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload(new byte[0]))
                .isInstanceOf(EmptyReceiptFileException.class);
        verify(storage, never()).save(any(), any(), any());
    }

    @Test
    void rejectsOversizedFileBeforeMagicBytesCheck() {
        byte[] oversized = new byte[(int) ReceiptService.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> service.upload(oversized))
                .isInstanceOf(ReceiptTooLargeException.class);
        verify(storage, never()).save(any(), any(), any());
    }

    @Test
    void rejectsInvalidMagicBytes() {
        byte[] invalid = "not an image".getBytes();

        assertThatThrownBy(() -> service.upload(invalid))
                .isInstanceOf(UnsupportedReceiptMediaTypeException.class);
        verify(storage, never()).save(any(), any(), any());
    }

    @Test
    void uploadsValidPngAndStoresWithDetectedType() {
        when(currentUserProvider.get())
                .thenReturn(new CurrentUser(ownerId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY));
        String key = familyId + "/" + UUID.randomUUID() + ".png";
        when(storage.save(eq(familyId), eq("png"), eq(PNG_BYTES))).thenReturn(key);
        when(repository.saveAndFlush(any(ReceiptAttachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReceiptAttachment result = service.upload(PNG_BYTES);

        assertThat(result.getFamilyId()).isEqualTo(familyId);
        assertThat(result.getUploadedByUserId()).isEqualTo(ownerId);
        assertThat(result.getStorageKey()).isEqualTo(key);
        assertThat(result.getMimeType()).isEqualTo("image/png");
        assertThat(result.getFileSizeBytes()).isEqualTo(PNG_BYTES.length);
        assertThat(result.getOcrStatus()).isEqualTo(ReceiptOcrStatus.UPLOADED);
    }

    @Test
    void cleansUpStoredFileWhenDatabaseSaveFails() {
        when(currentUserProvider.get())
                .thenReturn(new CurrentUser(ownerId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY));
        String key = familyId + "/" + UUID.randomUUID() + ".png";
        when(storage.save(eq(familyId), eq("png"), eq(PNG_BYTES))).thenReturn(key);
        when(repository.saveAndFlush(any(ReceiptAttachment.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.upload(PNG_BYTES))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).deleteQuietly(key);
    }

    @Test
    void allowsOwnerAndAdultButHidesReceiptFromOtherChildWithNotFound() {
        UUID receiptId = UUID.randomUUID();
        ReceiptAttachment attachment =
                new ReceiptAttachment(familyId, ownerId, familyId + "/a.png", "image/png", 10);
        when(repository.findByIdAndFamilyId(receiptId, familyId))
                .thenReturn(Optional.of(attachment));

        when(currentUserProvider.get())
                .thenReturn(new CurrentUser(ownerId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY));
        assertThat(service.getById(receiptId)).isSameAs(attachment);

        when(currentUserProvider.get())
                .thenReturn(
                        new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL));
        assertThat(service.getById(receiptId)).isSameAs(attachment);

        when(currentUserProvider.get())
                .thenReturn(
                        new CurrentUser(otherChildId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY));
        assertThatThrownBy(() -> service.getById(receiptId))
                .isInstanceOf(NotFoundException.class);
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}
