package com.familion.economics.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.economics.application.EconomicsSyncService;
import com.familion.economics.application.api.EconomicSnapshotData;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.economics.application.api.FamilyEconomicContext;
import com.familion.economics.domain.EconomicIndex;
import com.familion.economics.domain.IndexCode;
import com.familion.economics.domain.IndexSource;
import com.familion.economics.infrastructure.cache.RedisEconomicIndexCache;
import com.familion.economics.infrastructure.persistence.EconomicIndexBatchRepository;
import com.familion.economics.infrastructure.persistence.EconomicIndexBatchRepository.UpsertEntry;
import com.familion.economics.infrastructure.persistence.EconomicIndexRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-",
            "ECON_DOLARAPI_URL=http://127.0.0.1:1",
            "ECON_ARGDATOS_URL=http://127.0.0.1:1"
        })
class EconomicsControllerIT extends IntegrationTestBase {

    @Autowired
    private EconomicIndexRepository repository;

    @Autowired
    private EconomicIndexBatchRepository batchRepository;

    @Autowired
    private EconomicsSyncService syncService;

    @Autowired
    private EconomicsApi economicsApi;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String adultBearer;
    private String childBearer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }
        UUID familyId = UUID.randomUUID();
        adultBearer =
                tokens.bearer(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childBearer =
                tokens.bearer(UUID.randomUUID(), familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
    }

    @Test
    void returns401WhenUnauthenticated() {
        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/economics/indices/latest")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void latestEndpointReturnsMostRecentValuePerIndexCodeAndPopulatesRedisCache() {
        batchRepository.batchUpsertFromApi(
                List.of(
                        new UpsertEntry(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 20),
                                new BigDecimal("1649.80")),
                        new UpsertEntry(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1743.10")),
                        new UpsertEntry(
                                IndexCode.UVA,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("2206.78"))));

        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/economics/indices/latest")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).path("indexCode").asText()).isEqualTo("USD_MEP");
        assertThat(response.getBody().get(0).path("effectiveDate").asText()).isEqualTo("2026-11-23");
        assertThat(response.getBody().get(0).path("value").asText()).isEqualTo("1743.100000");
        assertThat(response.getBody().get(0).path("source").asText()).isEqualTo("API");
        assertThat(response.getBody().get(1).path("indexCode").asText()).isEqualTo("UVA");
        assertThat(response.getBody().get(1).path("value").asText()).isEqualTo("2206.780000");

        assertThat(redisTemplate.opsForValue().get(RedisEconomicIndexCache.latestKey(IndexCode.USD_MEP)))
                .isNotNull();
    }

    @Test
    void historyEndpointReturnsAscendingValuesAndRejectsRangeExceedingTwoYears() {
        batchRepository.batchUpsertFromApi(
                List.of(
                        new UpsertEntry(
                                IndexCode.USD_BLUE,
                                LocalDate.of(2026, 10, 31),
                                new BigDecimal("1590.00")),
                        new UpsertEntry(
                                IndexCode.USD_BLUE,
                                LocalDate.of(2026, 11, 1),
                                new BigDecimal("1600.00")),
                        new UpsertEntry(
                                IndexCode.USD_BLUE,
                                LocalDate.of(2026, 11, 15),
                                new BigDecimal("1650.00")),
                        new UpsertEntry(
                                IndexCode.USD_BLUE,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1675.00"))));

        ResponseEntity<JsonNode> ok = client()
                .get()
                .uri("/api/v1/economics/indices/history?indexCode=USD_BLUE&from=2026-11-01&to=2026-11-20")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).isNotNull();
        assertThat(ok.getBody()).hasSize(2);
        assertThat(ok.getBody().get(0).path("effectiveDate").asText()).isEqualTo("2026-11-01");
        assertThat(ok.getBody().get(0).path("value").asText()).isEqualTo("1600.000000");
        assertThat(ok.getBody().get(1).path("effectiveDate").asText()).isEqualTo("2026-11-15");
        assertThat(ok.getBody().get(1).path("value").asText()).isEqualTo("1650.000000");

        assertThat(redisTemplate.opsForValue()
                        .get(RedisEconomicIndexCache.historyKey(IndexCode.USD_BLUE, YearMonth.of(2026, 11))))
                .isNotNull();

        ResponseEntity<JsonNode> exactTwoYears = client()
                .get()
                .uri("/api/v1/economics/indices/history?indexCode=USD_BLUE&from=2024-11-01&to=2026-11-01")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(exactTwoYears.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> overTwoYears = client()
                .get()
                .uri("/api/v1/economics/indices/history?indexCode=USD_BLUE&from=2024-11-01&to=2026-11-02")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(overTwoYears.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(overTwoYears.getHeaders().getContentType())
                .isNotNull()
                .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(overTwoYears.getBody()).isNotNull();
        assertThat(overTwoYears.getBody().path("status").asInt()).isEqualTo(400);

        ResponseEntity<JsonNode> invertedRange = client()
                .get()
                .uri("/api/v1/economics/indices/history?indexCode=USD_BLUE&from=2026-11-20&to=2026-11-01")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(invertedRange.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void manualEndpointForbidsChildAndAllowsAdultAndNeverGetsOverwrittenByApiSync() {
        Map<String, Object> payload = Map.of(
                "indexCode", "USD_MEP",
                "effectiveDate", "2026-11-23",
                "value", "1800.50");

        ResponseEntity<JsonNode> forbidden = client()
                .post()
                .uri("/api/v1/economics/indices/manual")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getHeaders().getContentType())
                .isNotNull()
                .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Seed an API value first and warm cache
        batchRepository.batchUpsertFromApi(
                List.of(
                        new UpsertEntry(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 22),
                                new BigDecimal("1700.00")),
                        new UpsertEntry(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1743.10"))));
        client().get()
                .uri("/api/v1/economics/indices/latest")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        // Adult manually overrides 2026-11-23
        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/economics/indices/manual")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().path("indexCode").asText()).isEqualTo("USD_MEP");
        assertThat(created.getBody().path("effectiveDate").asText()).isEqualTo("2026-11-23");
        assertThat(created.getBody().path("value").asText()).isEqualTo("1800.500000");
        assertThat(created.getBody().path("source").asText()).isEqualTo("MANUAL");

        // Cache must have been invalidated, so /latest reflects MANUAL value
        ResponseEntity<JsonNode> latestAfterManual = client()
                .get()
                .uri("/api/v1/economics/indices/latest")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(latestAfterManual.getBody()).isNotNull();
        assertThat(latestAfterManual.getBody().get(0).path("value").asText())
                .isEqualTo("1800.500000");
        assertThat(latestAfterManual.getBody().get(0).path("source").asText()).isEqualTo("MANUAL");

        // Subsequent API sync must NOT overwrite the MANUAL row, but MUST update the API row
        batchRepository.batchUpsertFromApi(
                List.of(
                        new UpsertEntry(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 22),
                                new BigDecimal("1710.00")),
                        new UpsertEntry(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1749.99"))));

        EconomicIndex manualRow = repository
                .findByIndexCodeAndEffectiveDate(IndexCode.USD_MEP, LocalDate.of(2026, 11, 23))
                .orElseThrow();
        assertThat(manualRow.getValue()).isEqualByComparingTo("1800.500000");
        assertThat(manualRow.getSource()).isEqualTo(IndexSource.MANUAL);

        EconomicIndex apiRow = repository
                .findByIndexCodeAndEffectiveDate(IndexCode.USD_MEP, LocalDate.of(2026, 11, 22))
                .orElseThrow();
        assertThat(apiRow.getValue()).isEqualByComparingTo("1710.000000");
        assertThat(apiRow.getSource()).isEqualTo(IndexSource.API);
    }

    @Test
    void manualEndpointRejectsNonPositiveValueWith400() {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/economics/indices/manual")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "indexCode", "USD_MEP",
                        "effectiveDate", "2026-11-23",
                        "value", "0"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void manualIpcMensualRecalculatesIpcAccumulatedSeries() {
        postManualIpc("2026-01-15", "2.5");
        postManualIpc("2026-02-01", "3.0");
        postManualIpc("2026-03-01", "2.0");

        List<EconomicIndex> accumulated =
                repository.findByIndexCodeOrderByEffectiveDateAsc(IndexCode.IPC_ACUMULADO);
        assertThat(accumulated).hasSize(3);
        assertThat(accumulated.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(accumulated.get(0).getValue()).isEqualByComparingTo("102.500000");
        assertThat(accumulated.get(1).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(accumulated.get(1).getValue()).isEqualByComparingTo("105.575000");
        assertThat(accumulated.get(2).getEffectiveDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(accumulated.get(2).getValue()).isEqualByComparingTo("107.686500");
    }

    @Test
    void syncFailsGracefullyWithoutInternetAndEndpointsStillRespond() {
        assertThatCode(() -> syncService.syncLatest()).doesNotThrowAnyException();

        ResponseEntity<JsonNode> latest = client()
                .get()
                .uri("/api/v1/economics/indices/latest")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(latest.getBody()).isNotNull();
        assertThat(latest.getBody().isArray()).isTrue();
    }

    @Test
    void economicsApiSnapshotsAndConvertsAgainstPersistedIndices() {
        batchRepository.batchUpsertFromApi(
                List.of(
                        new UpsertEntry(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 20),
                                new BigDecimal("1500.00")),
                        new UpsertEntry(
                                IndexCode.UVA,
                                LocalDate.of(2026, 11, 20),
                                new BigDecimal("2200.00")),
                        new UpsertEntry(
                                IndexCode.IPC_ACUMULADO,
                                LocalDate.of(2026, 11, 1),
                                new BigDecimal("107.686500"))));

        FamilyEconomicContext ctx = new FamilyEconomicContext("ARS", "USD", "USD_MEP");
        EconomicSnapshotData snapshot = economicsApi.snapshotFor(
                ctx, LocalDate.of(2026, 11, 22), new BigDecimal("300000"), "ARS");

        assertThat(snapshot.referenceExchangeRate()).isEqualTo(new BigDecimal("1500.000000"));
        assertThat(snapshot.baseCurrencyAmount()).isEqualTo(new BigDecimal("300000.0000"));
        assertThat(snapshot.amountInReference()).isEqualTo(new BigDecimal("200.0000"));
        assertThat(snapshot.uvaValue()).isEqualTo(new BigDecimal("2200.000000"));
        assertThat(snapshot.ipcIndexValue()).isEqualTo(new BigDecimal("107.686500"));
        assertThat(snapshot.snapshotDate()).isEqualTo(LocalDate.of(2026, 11, 22));

        assertThat(economicsApi.valueAt("USD_MEP", LocalDate.of(2026, 11, 22)))
                .contains(new BigDecimal("1500.000000"));
        assertThat(economicsApi.latestValue("UVA")).contains(new BigDecimal("2200.000000"));
        assertThat(economicsApi.convert(
                        new BigDecimal("150000"),
                        "ARS",
                        "USD",
                        LocalDate.of(2026, 11, 22),
                        "USD_MEP"))
                .isEqualTo(new BigDecimal("100.0000"));
    }

    private void postManualIpc(String effectiveDate, String value) {
        ResponseEntity<JsonNode> res = client()
                .post()
                .uri("/api/v1/economics/indices/manual")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "indexCode", "IPC_MENSUAL",
                        "effectiveDate", effectiveDate,
                        "value", value))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
