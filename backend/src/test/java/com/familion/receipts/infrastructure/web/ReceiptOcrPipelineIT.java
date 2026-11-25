package com.familion.receipts.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.receipts.application.port.ReceiptDraft;
import com.familion.receipts.application.port.ReceiptParserPort;
import com.familion.receipts.domain.ReceiptParsingException;
import com.familion.receipts.infrastructure.ocr.HeuristicTicketTextParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-",
            "spring.datasource.hikari.maximum-pool-size=3",
            "familion.receipts.tesseract-enabled=false"
        })
class ReceiptOcrPipelineIT extends IntegrationTestBase {

    private static final Path TEMP_STORAGE_DIR = createTempStorageDir();

    private static final byte[] SMALL_PNG = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
        (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
        0x42, 0x60, (byte) 0x82
    };

    private static final byte[] MINIMAL_PDF =
            "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @DynamicPropertySource
    static void registerStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("familion.receipts.storage-dir", () -> TEMP_STORAGE_DIR.toString());
    }

    @AfterAll
    static void cleanUpTempStorage() throws IOException {
        if (Files.exists(TEMP_STORAGE_DIR)) {
            try (var stream = Files.walk(TEMP_STORAGE_DIR)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best-effort test cleanup.
                    }
                });
            }
        }
    }

    @MockitoBean
    private ReceiptParserPort parserPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adultBearer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID familyId = fixtures.createFamily("Familia OCR " + suffix);
        UUID adultId = fixtures.createUser(
                familyId, "ocr." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        adultBearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void asyncOcrParsesDraftResolvesSeededCategoryAndPersistsJsonb() throws Exception {
        String rawTicket = """
                TICKET FACTURA B
                COTO C.I.C.S.A.
                FECHA: 14/03/2026
                TOTAL A PAGAR $ 38.750,50
                """;
        ReceiptDraft heuristicDraft = new HeuristicTicketTextParser().parse(rawTicket);
        when(parserPort.parse(any(byte[].class), eq("image/png"))).thenReturn(heuristicDraft);

        UUID receiptId = upload(SMALL_PNG, "ticket.png");
        JsonNode result = pollUntilTerminal(receiptId);

        assertThat(result.path("status").asText()).isEqualTo("PARSED");
        JsonNode draft = result.path("draft");
        assertThat(draft.path("merchant").asText()).isEqualTo("COTO C.I.C.S.A.");
        assertThat(draft.path("date").asText()).isEqualTo("2026-03-14");
        assertThat(draft.path("totalAmount").asText()).isEqualTo("38750.5000");
        assertThat(draft.path("currencyCode").asText()).isEqualTo("ARS");
        assertThat(draft.path("suggestedCategoryKey").asText()).isEqualTo("SUPERMARKET");
        assertThat(draft.path("confidence").asText()).isEqualTo("1.00");
        assertThat(draft.path("rawText").asText()).isEqualTo(rawTicket);

        String expectedSupermarketCategoryId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM categories WHERE system_key = 'SUPERMARKET'", String.class);
        assertThat(draft.path("suggestedCategoryId").asText())
                .isEqualTo(expectedSupermarketCategoryId);

        String persistedMerchant = jdbcTemplate.queryForObject(
                "SELECT ocr_raw_payload->>'merchant' FROM receipt_attachments WHERE id = ?::uuid",
                String.class,
                receiptId.toString());
        assertThat(persistedMerchant).isEqualTo("COTO C.I.C.S.A.");
    }

    @Test
    void asyncOcrMarksFailedAndStoresReasonWhenParserThrows() throws Exception {
        when(parserPort.parse(any(byte[].class), eq("application/pdf")))
                .thenThrow(new ReceiptParsingException("PDF OCR is not supported without pdftoppm"));

        UUID receiptId = upload(MINIMAL_PDF, "ticket.pdf");
        JsonNode result = pollUntilTerminal(receiptId);

        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("draft").isNull()).isTrue();

        String persistedError = jdbcTemplate.queryForObject(
                "SELECT ocr_raw_payload->>'error' FROM receipt_attachments WHERE id = ?::uuid",
                String.class,
                receiptId.toString());
        assertThat(persistedError).contains("PDF OCR is not supported without pdftoppm");
    }

    private UUID upload(byte[] bytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(bytes, filename));

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/receipts/scan")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return UUID.fromString(resp.getBody().path("id").asText());
    }

    private JsonNode pollUntilTerminal(UUID receiptId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<JsonNode> resp = client()
                    .get()
                    .uri("/api/v1/receipts/" + receiptId)
                    .header(HttpHeaders.AUTHORIZATION, adultBearer)
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = resp.getBody();
            assertThat(body).isNotNull();
            String status = body.path("status").asText();
            if ("PARSED".equals(status) || "FAILED".equals(status)) {
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Receipt " + receiptId + " did not reach terminal status in time");
    }

    private static Path createTempStorageDir() {
        try {
            return Files.createTempDirectory("familion-receipts-ocr-it-");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create temp directory for receipts OCR IT", ex);
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
