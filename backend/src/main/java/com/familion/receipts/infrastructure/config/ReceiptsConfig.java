package com.familion.receipts.infrastructure.config;

import com.familion.receipts.application.port.ReceiptStorage;
import com.familion.receipts.infrastructure.storage.LocalFilesystemReceiptStorage;
import jakarta.servlet.MultipartConfigElement;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class ReceiptsConfig {

    @Bean
    public ReceiptStorage receiptStorage(
            @Value("${familion.receipts.storage-dir:${RECEIPTS_STORAGE_DIR:/data/receipts}}")
                    String storageDir) {
        return new LocalFilesystemReceiptStorage(Path.of(storageDir));
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        // Allow slightly above the 5 MB application limit so oversized uploads reach
        // ReceiptService and return a clean 413 ProblemDetail without container resets.
        factory.setMaxFileSize(DataSize.ofMegabytes(10));
        factory.setMaxRequestSize(DataSize.ofMegabytes(12));
        return factory.createMultipartConfig();
    }
}
