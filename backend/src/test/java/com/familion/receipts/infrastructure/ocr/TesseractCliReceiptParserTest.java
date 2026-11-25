package com.familion.receipts.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.receipts.application.port.ReceiptDraft;
import com.familion.receipts.domain.ReceiptParsingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesseractCliReceiptParserTest {

    @TempDir
    Path tempDir;

    private final HeuristicTicketTextParser textParser = new HeuristicTicketTextParser();

    @Test
    void rejectsPdfWhenPdftoppmIsUnavailable() {
        TesseractCliReceiptParser parser = new TesseractCliReceiptParser(
                "tesseract", "pdftoppm", false, "spa+eng", Duration.ofSeconds(5), textParser);

        assertThatThrownBy(() -> parser.parse("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "application/pdf"))
                .isInstanceOf(ReceiptParsingException.class)
                .hasMessageContaining("PDF OCR is not supported");
    }

    @Test
    void invokesCliWithStdinStdoutAndLanguageAndParsesOutput() throws IOException {
        Path fakeTesseract = writeExecutable(
                "fake-tesseract.sh",
                """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "tesseract 5.3.0"
                  exit 0
                fi
                if [ "$1" != "stdin" ] || [ "$2" != "stdout" ] || [ "$3" != "-l" ] || [ "$4" != "spa+eng" ]; then
                  echo "unexpected args: $@" >&2
                  exit 2
                fi
                cat >/dev/null
                printf 'TICKET B\\nCOTO C.I.C.S.A.\\n14/03/2026\\nTOTAL $ 1.234,56\\n'
                """);

        assertThat(TesseractCliReceiptParser.isBinaryAvailable(fakeTesseract.toString())).isTrue();

        TesseractCliReceiptParser parser = new TesseractCliReceiptParser(
                fakeTesseract.toString(),
                "pdftoppm",
                false,
                "spa+eng",
                Duration.ofSeconds(5),
                textParser);

        ReceiptDraft draft = parser.parse(new byte[] {1, 2, 3, 4}, "image/png");

        assertThat(draft.merchant()).isEqualTo("COTO C.I.C.S.A.");
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(draft.totalAmount()).isEqualByComparingTo(new BigDecimal("1234.5600"));
        assertThat(draft.currencyCode()).isEqualTo("ARS");
        assertThat(draft.suggestedCategoryKey()).isEqualTo("SUPERMARKET");
        assertThat(draft.confidence()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    void throwsParsingExceptionOnNonZeroExitCode() throws IOException {
        Path failingBinary = writeExecutable(
                "fail-tesseract.sh",
                """
                #!/bin/sh
                echo "corrupted image stream" >&2
                exit 1
                """);

        TesseractCliReceiptParser parser = new TesseractCliReceiptParser(
                failingBinary.toString(),
                "pdftoppm",
                false,
                "spa+eng",
                Duration.ofSeconds(5),
                textParser);

        assertThatThrownBy(() -> parser.parse(new byte[] {1, 2}, "image/png"))
                .isInstanceOf(ReceiptParsingException.class)
                .hasMessageContaining("exit code 1")
                .hasMessageContaining("corrupted image stream");
    }

    @Test
    void throwsParsingExceptionWhenProcessTimesOut() throws IOException {
        Path slowBinary = writeExecutable(
                "slow-tesseract.sh",
                """
                #!/bin/sh
                sleep 5
                """);

        TesseractCliReceiptParser parser = new TesseractCliReceiptParser(
                slowBinary.toString(),
                "pdftoppm",
                false,
                "spa+eng",
                Duration.ofMillis(200),
                textParser);

        assertThatThrownBy(() -> parser.parse(new byte[] {1, 2}, "image/png"))
                .isInstanceOf(ReceiptParsingException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void reportsUnavailableForMissingBinary() {
        assertThat(TesseractCliReceiptParser.isBinaryAvailable("non-existent-tesseract-binary-xyz"))
                .isFalse();
    }

    private Path writeExecutable(String filename, String content) throws IOException {
        Path script = tempDir.resolve(filename);
        Files.writeString(script, content, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }
}
