package com.familion.receipts.infrastructure.config;

import com.familion.receipts.application.port.ReceiptParserPort;
import com.familion.receipts.application.port.ReceiptStorage;
import com.familion.receipts.infrastructure.ocr.HeuristicTicketTextParser;
import com.familion.receipts.infrastructure.ocr.NoopReceiptParser;
import com.familion.receipts.infrastructure.ocr.TesseractCliReceiptParser;
import com.familion.receipts.infrastructure.storage.LocalFilesystemReceiptStorage;
import jakarta.servlet.MultipartConfigElement;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.unit.DataSize;

@Configuration
@EnableAsync
public class ReceiptsConfig {

    public static final String OCR_EXECUTOR_BEAN = "receiptOcrExecutor";

    private static final Logger log = LoggerFactory.getLogger(ReceiptsConfig.class);

    @Bean
    public ReceiptStorage receiptStorage(
            @Value("${familion.receipts.storage-dir:${RECEIPTS_STORAGE_DIR:/data/receipts}}")
                    String storageDir) {
        return new LocalFilesystemReceiptStorage(Path.of(storageDir));
    }

    @Bean(name = OCR_EXECUTOR_BEAN)
    public Executor receiptOcrExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("receipt-ocr-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    @Bean
    public HeuristicTicketTextParser heuristicTicketTextParser() {
        return new HeuristicTicketTextParser();
    }

    @Bean
    @ConditionalOnMissingBean(ReceiptParserPort.class)
    public ReceiptParserPort receiptParserPort(
            HeuristicTicketTextParser heuristicTicketTextParser,
            @Value("${familion.receipts.tesseract-enabled:true}") boolean tesseractEnabled,
            @Value("${familion.receipts.tesseract-binary:tesseract}") String tesseractBinary,
            @Value("${familion.receipts.ocr-timeout:PT30S}") Duration ocrTimeout) {
        if (!tesseractEnabled) {
            log.info("Tesseract OCR disabled via configuration; using NoopReceiptParser");
            return new NoopReceiptParser();
        }
        if (TesseractCliReceiptParser.isBinaryAvailable(tesseractBinary)) {
            boolean pdftoppmAvailable = TesseractCliReceiptParser.isPdftoppmAvailable();
            log.info(
                    "Tesseract binary '{}' detected; activating TesseractCliReceiptParser (pdftoppm={})",
                    tesseractBinary,
                    pdftoppmAvailable);
            return new TesseractCliReceiptParser(
                    tesseractBinary, ocrTimeout, pdftoppmAvailable, heuristicTicketTextParser);
        }
        log.info(
                "Tesseract binary '{}' not available; falling back to NoopReceiptParser",
                tesseractBinary);
        return new NoopReceiptParser();
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
