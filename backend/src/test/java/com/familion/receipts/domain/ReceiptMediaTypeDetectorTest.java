package com.familion.receipts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReceiptMediaTypeDetectorTest {

    @Test
    void detectsJpegByMagicBytes() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        assertThat(ReceiptMediaTypeDetector.detect(jpeg)).contains(ReceiptMediaType.JPEG);
    }

    @Test
    void detectsPngByMagicBytes() {
        byte[] png = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
        };
        assertThat(ReceiptMediaTypeDetector.detect(png)).contains(ReceiptMediaType.PNG);
    }

    @Test
    void detectsWebpByMagicBytes() {
        byte[] webp = "RIFF\u0024\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ReceiptMediaTypeDetector.detect(webp)).contains(ReceiptMediaType.WEBP);
    }

    @Test
    void detectsPdfByMagicBytes() {
        byte[] pdf = "%PDF-1.7\n%...".getBytes(StandardCharsets.US_ASCII);
        assertThat(ReceiptMediaTypeDetector.detect(pdf)).contains(ReceiptMediaType.PDF);
    }

    @Test
    void rejectsRiffContainerThatIsNotWebp() {
        byte[] wav = "RIFF\u0024\u0000\u0000\u0000WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ReceiptMediaTypeDetector.detect(wav)).isEmpty();
    }

    @Test
    void rejectsPlainTextAndEmptyInputs() {
        assertThat(ReceiptMediaTypeDetector.detect("hello world".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(ReceiptMediaTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(ReceiptMediaTypeDetector.detect(null)).isEmpty();
        assertThat(ReceiptMediaTypeDetector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8}))
                .isEmpty();
    }
}
