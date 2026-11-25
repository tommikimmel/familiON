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
class MaintenanceLogControllerIT extends IntegrationTestBase {

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
        familyId = fixtures.createFamily("Familia Mantenimiento " + suffix);
        adultUserId = fixtures.createUser(
                familyId, "adulto." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childUserId = fixtures.createUser(
                familyId, "hijo." + suffix + "@familion.local", Role.HIJO, FamilyVisibility.OWN_ONLY);

        accountId = fixtures.createAccount(familyId, adultUserId, "BANK", "ARS");

        adultBearer = tokens.bearer(adultUserId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childBearer = tokens.bearer(childUserId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        Map<String, Object> vehicleBody = Map.of(
                "nickname", "Corolla",
                "brand", "Toyota",
                "model", "Corolla",
                "modelYear", 2021,
                "licensePlate", "AE" + suffix.substring(0, 4) + "ZZ",
                "primaryFuelType", "NAFTA_SUPER",
                "currentOdometerKm", 60000);

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
    @DisplayName("Service without cost does not create a transaction")
    void serviceWithoutCostDoesNotCreateTransaction() {
        int initialTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ?::uuid",
                Integer.class,
                familyId);

        Map<String, Object> body = Map.of(
                "maintenanceType", "SERVICE",
                "odometerKm", 60000,
                "description", "Revisión general en garantía",
                "costAmount", 0,
                "currencyCode", "ARS");

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/maintenance-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("id")).isNotNull();
        assertThat(resp.getBody().get("transactionId").isNull()).isTrue();

        int afterTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ?::uuid",
                Integer.class,
                familyId);
        assertThat(afterTxCount).isEqualTo(initialTxCount);
    }

    @Test
    @DisplayName("Maintenance with cost creates EXPENSE transaction with VEHICLES_MAINTENANCE and updates odometer")
    void maintenanceWithCostCreatesTransaction() {
        Map<String, Object> body = Map.of(
                "maintenanceType", "OIL_CHANGE",
                "odometerKm", 65000,
                "description", "Cambio de aceite sintético y filtro",
                "costAmount", 85000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString(),
                "nextDueKm", 75000,
                "workshopName", "Lubricentro Norte");

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/maintenance-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String txId = resp.getBody().get("transactionId").asText();
        assertThat(txId).isNotBlank();

        Map<String, Object> tx = jdbcTemplate.queryForMap(
                "SELECT t.type, t.description, t.amount, c.system_key "
                        + "FROM transactions t JOIN categories c ON t.category_id = c.id "
                        + "WHERE t.id = ?::uuid",
                txId);
        assertThat(tx.get("type")).isEqualTo("EXPENSE");
        assertThat(tx.get("description")).isEqualTo("Cambio de aceite · Corolla");
        assertThat(((BigDecimal) tx.get("amount"))).isEqualByComparingTo(new BigDecimal("85000.0000"));
        assertThat(tx.get("system_key")).isEqualTo("VEHICLES_MAINTENANCE");

        // Vehicle odometer updated
        ResponseEntity<JsonNode> vResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(vResp.getBody().get("currentOdometerKm").asInt()).isEqualTo(65000);
    }

    @Test
    @DisplayName("nextDueKm <= odometerKm returns 422")
    void nextDueKmInvalidReturns422() {
        Map<String, Object> body = Map.of(
                "maintenanceType", "BRAKES",
                "odometerKm", 61000,
                "description", "Pastillas de freno",
                "costAmount", 0,
                "currencyCode", "ARS",
                "nextDueKm", 60500);

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/maintenance-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("VTV maintenance with nextDueDate updates vtv_expiration_date")
    void vtvUpdatesExpirationDate() {
        LocalDate newVtv = LocalDate.now().plusMonths(12);
        Map<String, Object> body = Map.of(
                "maintenanceType", "VTV",
                "odometerKm", 62000,
                "description", "Verificación Técnica Vehicular aprobada",
                "costAmount", 0,
                "currencyCode", "ARS",
                "nextDueDate", newVtv.toString());

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/maintenance-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> vResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(vResp.getBody().get("vtvExpirationDate").asText()).isEqualTo(newVtv.toString());
    }
}
