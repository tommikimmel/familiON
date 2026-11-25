package com.familion.receipts.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
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
class ReceiptControllerIT extends IntegrationTestBase {

    private static final Path TEMP_STORAGE_DIR = createTempStorageDir();

    // Minimal valid 1x1 transparent PNG
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID familyId;
    private UUID otherFamilyId;
    private String childOwnerBearer;
    private String otherChildBearer;
    private String adultBearer;
    private String otherFamilyAdultBearer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        familyId = fixtures.createFamily("Familia Receipts " + suffix);
        otherFamilyId = fixtures.createFamily("Otra Familia " + suffix);

        UUID childOwnerId = fixtures.createUser(
                familyId, "hijo1." + suffix + "@familion.local", Role.HIJO, FamilyVisibility.OWN_ONLY);
        UUID otherChildId = fixtures.createUser(
                familyId, "hijo2." + suffix + "@familion.local", Role.HIJO, FamilyVisibility.OWN_ONLY);
        UUID adultId = fixtures.createUser(
                familyId, "adulto." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        UUID otherFamilyAdultId = fixtures.createUser(
                otherFamilyId,
                "otro." + suffix + "@familion.local",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);

        childOwnerBearer =
                tokens.bearer(childOwnerId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        otherChildBearer =
                tokens.bearer(otherChildId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        otherFamilyAdultBearer = tokens.bearer(
                otherFamilyAdultId, otherFamilyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void uploadPngStoresOnDiskPollsToParsedWithNoopAndDownloadsIdenticalBytes() throws Exception {
        // Upload with a misleading client filename to verify magic-byte detection and safe storage naming
        ResponseEntity<JsonNode> uploadResponse =
                uploadMultipart(childOwnerBearer, SMALL_PNG, "../../evil-client-name.txt");

        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode created = uploadResponse.getBody();
        assertThat(created).isNotNull();
        UUID receiptId = UUID.fromString(created.path("id").asText());
        assertThat(created.path("status").asText()).isEqualTo("UPLOADED");
        assertThat(created.path("mimeType").asText()).isEqualTo("image/png");
        assertThat(created.path("fileSizeBytes").asLong()).isEqualTo(SMALL_PNG.length);
        assertThat(created.path("createdAt").asText()).isNotBlank();
        assertThat(created.path("fileUrl").asText())
                .isEqualTo("/api/v1/receipts/" + receiptId + "/file");

        // Verify relative storage_key and file existence on disk under {storageDir}/{familyId}/{uuid}.png
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM receipt_attachments WHERE id = ?::uuid",
                String.class,
                receiptId.toString());
        assertThat(storageKey)
                .startsWith(familyId + "/")
                .endsWith(".png")
                .doesNotContain("evil-client-name");
        Path storedFile = TEMP_STORAGE_DIR.resolve(storageKey);
        assertThat(Files.isRegularFile(storedFile)).isTrue();
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(SMALL_PNG);

        // Poll until NoopReceiptParser finishes async OCR -> PARSED with confidence 0
        JsonNode parsed = pollUntilTerminal(childOwnerBearer, receiptId);
        assertThat(parsed.path("status").asText()).isEqualTo("PARSED");
        assertThat(parsed.path("draft").isObject()).isTrue();
        assertThat(parsed.path("draft").path("merchant").isNull()).isTrue();
        assertThat(parsed.path("draft").path("confidence").asText()).isEqualTo("0");

        // Owner download returns identical bytes and detected Content-Type
        ResponseEntity<byte[]> downloadResponse = client()
                .get()
                .uri("/api/v1/receipts/" + receiptId + "/file")
                .header(HttpHeaders.AUTHORIZATION, childOwnerBearer)
                .retrieve()
                .toEntity(byte[].class);
        assertThat(downloadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downloadResponse.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(downloadResponse.getBody()).isEqualTo(SMALL_PNG);

        // Adult in the same family can read both metadata and file
        ResponseEntity<JsonNode> adultGet = client()
                .get()
                .uri("/api/v1/receipts/" + receiptId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(adultGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> adultDownload = client()
                .get()
                .uri("/api/v1/receipts/" + receiptId + "/file")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(byte[].class);
        assertThat(adultDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adultDownload.getBody()).isEqualTo(SMALL_PNG);

        // Another HIJO in the same family gets 404 on metadata and file
        ResponseEntity<JsonNode> otherChildGet = getAllowingErrors(otherChildBearer, "/api/v1/receipts/" + receiptId);
        assertThat(otherChildGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> otherChildFile =
                getAllowingErrors(otherChildBearer, "/api/v1/receipts/" + receiptId + "/file");
        assertThat(otherChildFile.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Adult from another family also gets 404
        ResponseEntity<JsonNode> otherFamilyGet =
                getAllowingErrors(otherFamilyAdultBearer, "/api/v1/receipts/" + receiptId);
        assertThat(otherFamilyGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidMagicBytesWith415() {
        byte[] fakePng = "this is definitely not a png".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<JsonNode> response = uploadMultipart(adultBearer, fakePng, "receipt.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsFileLargerThanFiveMegabytesWith413() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(SMALL_PNG, 0, oversized, 0, SMALL_PNG.length);

        ResponseEntity<JsonNode> response = uploadMultipart(adultBearer, oversized, "big.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private ResponseEntity<JsonNode> uploadMultipart(String bearer, byte[] bytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(bytes, filename));

        return client()
                .post()
                .uri("/api/v1/receipts/scan")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> getAllowingErrors(String bearer, String uri) {
        return client()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private JsonNode pollUntilTerminal(String bearer, UUID receiptId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<JsonNode> resp = client()
                    .get()
                    .uri("/api/v1/receipts/" + receiptId)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
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
            return Files.createTempDirectory("familion-receipts-it-");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create temp directory for receipts IT", ex);
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
