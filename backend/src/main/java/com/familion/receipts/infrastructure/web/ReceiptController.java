package com.familion.receipts.infrastructure.web;

import com.familion.receipts.application.ReceiptFileContent;
import com.familion.receipts.application.ReceiptService;
import com.familion.receipts.domain.EmptyReceiptFileException;
import com.familion.receipts.domain.ReceiptAttachment;
import com.familion.receipts.domain.ReceiptTooLargeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> scan(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > ReceiptService.MAX_FILE_SIZE_BYTES) {
            throw new ReceiptTooLargeException(
                    "Receipt file exceeds the maximum allowed size of 5 MB");
        }
        if (file.isEmpty()) {
            throw new EmptyReceiptFileException("Receipt file must not be empty");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded receipt file", ex);
        }
        ReceiptAttachment attachment = receiptService.upload(content);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ReceiptResponse.from(attachment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceiptResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ReceiptResponse.from(receiptService.getById(id)));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID id) {
        ReceiptFileContent file = receiptService.getFile(id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }
}
