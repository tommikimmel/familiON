package com.familion.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-",
            "spring.datasource.hikari.maximum-pool-size=3"
        })
class FuelLogControllerIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID familyId;
    private UUID adultUserId;
    private UUID childUserId;
    private UUID accountId;
    private String adultBearer;
    private String childBearer;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        familyId = fixtures.createFamily("Familia Combustible " + suffix);
        adultUserId = fixtures.createUser(
                familyId, "adulto." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childUserId = fixtures.createUser(
                familyId, "hijo." + suffix + "@familion.local", Role.HIJO, FamilyVisibility.OWN_ONLY);

        accountId = fixtures.createAccount(familyId, adultUserId, "BANK", "ARS");

        adultBearer = tokens.bearer(adultUserId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childBearer = tokens.bearer(childUserId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        // Create vehicle
        Map<String, Object> vehicleBody = Map.of(
                "nickname", "Palio",
                "brand", "Fiat",
                "model", "Palio",
                "modelYear", 2017,
                "licensePlate", "AD" + suffix.substring(0, 4) + "XY",
                "primaryFuelType", "NAFTA_SUPER",
                "currentOdometerKm", 50000);

        ResponseEntity<JsonNode> vResp = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(vehicleBody)
                .retrieve()
                .toEntity(JsonNode.class);

        vehicleId = UUID.fromString(vResp.getBody().get("id").asText());
    }

    @Test
    @DisplayName("Fuel log creates EXPENSE transaction with VEHICLES_FUEL category and updates odometer")
    void fuelLogCreatesTransactionAndUpdatesOdometer() {
        Map<String, Object> fuelBody = Map.of(
                "fuelDate", LocalDate.now().toString(),
                "odometerKm", 50600,
                "fuelType", "NAFTA_SUPER",
                "liters", 45.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "fullTank", true,
                "stationName", "YPF ACA",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fuelBody)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("id")).isNotNull();
        String txId = resp.getBody().get("transactionId").asText();
        assertThat(txId).isNotBlank();
        assertThat(resp.getBody().get("totalAmount").asDouble()).isEqualTo(45000.0);
        assertThat(resp.getBody().get("user").get("id").asText()).isEqualTo(adultUserId.toString());

        // Verify transaction in database
        Map<String, Object> tx = jdbcTemplate.queryForMap(
                "SELECT t.type, t.description, t.amount, c.system_key "
                        + "FROM transactions t JOIN categories c ON t.category_id = c.id "
                        + "WHERE t.id = ?::uuid",
                txId);
        assertThat(tx.get("type")).isEqualTo("EXPENSE");
        assertThat(tx.get("description")).isEqualTo("Combustible · Palio");
        assertThat(((BigDecimal) tx.get("amount"))).isEqualByComparingTo(new BigDecimal("45000.0000"));
        assertThat(tx.get("system_key")).isEqualTo("VEHICLES_FUEL");

        // Verify vehicle odometer was updated
        ResponseEntity<JsonNode> vehicleResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(vehicleResp.getBody().get("currentOdometerKm").asInt()).isEqualTo(50600);
    }

    @Test
    @DisplayName("Incoherent odometer returns 422 Unprocessable Entity")
    void incoherentOdometerReturns422() {
        // Log on 2026-05-10 with 51000 km
        Map<String, Object> log1 = Map.of(
                "fuelDate", "2026-05-10",
                "odometerKm", 51000,
                "fuelType", "NAFTA_SUPER",
                "liters", 40.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> resp1 = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(log1)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Attempt log on 2026-05-01 (earlier) with 51500 km (greater than future log) -> 422
        Map<String, Object> logBeforeHigher = Map.of(
                "fuelDate", "2026-05-01",
                "odometerKm", 51500,
                "fuelType", "NAFTA_SUPER",
                "liters", 40.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> resp2 = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(logBeforeHigher)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Attempt log on 2026-05-20 (later) with 50500 km (less than previous log) -> 422
        Map<String, Object> logAfterLower = Map.of(
                "fuelDate", "2026-05-20",
                "odometerKm", 50500,
                "fuelType", "NAFTA_SUPER",
                "liters", 40.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> resp3 = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(logAfterLower)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(resp3.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Atomic rollback: error during fuel log creation rolls back transaction")
    void atomicRollbackOnError() {
        int initialTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ?::uuid",
                Integer.class,
                familyId);

        // Passing fuelType HIBRIDO fails validation before or during fuel log creation
        Map<String, Object> invalidBody = Map.of(
                "fuelDate", LocalDate.now().toString(),
                "odometerKm", 52000,
                "fuelType", "HIBRIDO",
                "liters", 30.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalidBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        int postTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ?::uuid",
                Integer.class,
                familyId);
        assertThat(postTxCount).isEqualTo(initialTxCount);
    }

    @Test
    @DisplayName("Delete fuel log soft-deletes the transaction and removes fuel log")
    void deleteFuelLogSoftDeletesTransaction() {
        Map<String, Object> fuelBody = Map.of(
                "fuelDate", LocalDate.now().toString(),
                "odometerKm", 53000,
                "fuelType", "NAFTA_SUPER",
                "liters", 40.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> createResp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fuelBody)
                .retrieve()
                .toEntity(JsonNode.class);
        String logId = createResp.getBody().get("id").asText();
        String txId = createResp.getBody().get("transactionId").asText();

        // HIJO cannot delete adult's log
        ResponseEntity<JsonNode> childDeleteResp = client()
                .delete()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs/" + logId)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childDeleteResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADULTO can delete
        ResponseEntity<Void> deleteResp = client()
                .delete()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs/" + logId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Transaction soft deleted
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM transactions WHERE id = ?::uuid",
                Boolean.class,
                txId);
        assertThat(deleted).isTrue();

        // Fuel log removed
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fuel_logs WHERE id = ?::uuid",
                Integer.class,
                logId);
        assertThat(logCount).isEqualTo(0);
    }
}
