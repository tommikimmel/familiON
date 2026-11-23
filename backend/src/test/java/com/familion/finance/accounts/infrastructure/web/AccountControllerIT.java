package com.familion.finance.accounts.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

class AccountControllerIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountQueryApi accountQueryApi;

    private RestClient patchCapableClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void currentBalanceComputesMixedTransactionTypesAndIgnoresProjectedCancelledAndDeleted() {
        UUID familyId = fixtures.createFamily("Familia Balance " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-bal-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        // Create checking account with initialBalance = 1000.50
        ResponseEntity<JsonNode> bankResp = client()
                .post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Banco Principal",
                        "accountType", "BANK",
                        "currencyCode", "ARS",
                        "initialBalance", new BigDecimal("1000.50")))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(bankResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID bankId = UUID.fromString(bankResp.getBody().path("id").asText());

        // Create credit card with initialBalance = 0
        ResponseEntity<JsonNode> cardResp = client()
                .post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Visa Oro",
                        "accountType", "CREDIT_CARD",
                        "currencyCode", "ARS",
                        "creditLimit", new BigDecimal("500000"),
                        "closingDay", 24,
                        "dueDay", 5))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(cardResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID cardId = UUID.fromString(cardResp.getBody().path("id").asText());

        // POSTED inflows: +500 + 200 + 150 + 50 = +900
        insertTransaction(familyId, adultId, bankId, "INCOME", "POSTED", "500.0000", null);
        insertTransaction(familyId, adultId, bankId, "TRANSFER_IN", "POSTED", "200.0000", null);
        insertTransaction(familyId, adultId, bankId, "EXCHANGE_IN", "POSTED", "150.0000", null);
        insertTransaction(familyId, adultId, bankId, "ALLOWANCE_IN", "POSTED", "50.0000", null);

        // POSTED outflows: -300 - 100 - 75.25 - 25 = -500.25
        insertTransaction(familyId, adultId, bankId, "EXPENSE", "POSTED", "300.0000", null);
        insertTransaction(familyId, adultId, bankId, "TRANSFER_OUT", "POSTED", "100.0000", null);
        insertTransaction(familyId, adultId, bankId, "EXCHANGE_OUT", "POSTED", "75.2500", null);
        insertTransaction(familyId, adultId, bankId, "ALLOWANCE_OUT", "POSTED", "25.0000", null);

        // Must be IGNORED: PROJECTED, CANCELLED, and soft-deleted POSTED
        insertTransaction(familyId, adultId, bankId, "INCOME", "PROJECTED", "9999.0000", null);
        insertTransaction(familyId, adultId, bankId, "EXPENSE", "CANCELLED", "8888.0000", null);
        insertTransaction(familyId, adultId, bankId, "EXPENSE", "POSTED", "7777.0000", Instant.now());

        // Credit card debt: 0 - 12500.50 = -12500.5000 (sign NOT inverted)
        insertTransaction(familyId, adultId, cardId, "EXPENSE", "POSTED", "12500.5000", null);
        insertTransaction(familyId, adultId, cardId, "EXPENSE", "PROJECTED", "4000.0000", null);

        // Expected bank balance: 1000.50 + 900 - 500.25 = 1400.2500
        assertThat(accountQueryApi.currentBalance(bankId)).isEqualByComparingTo("1400.2500");
        assertThat(accountQueryApi.currentBalance(cardId)).isEqualByComparingTo("-12500.5000");

        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/accounts?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(2);

        JsonNode bankNode = findById(listResp.getBody(), bankId);
        JsonNode cardNode = findById(listResp.getBody(), cardId);
        assertThat(bankNode.path("initialBalance").asText()).isEqualTo("1000.5000");
        assertThat(bankNode.path("currentBalance").asText()).isEqualTo("1400.2500");
        assertThat(bankNode.path("isActive").asBoolean()).isTrue();

        assertThat(cardNode.path("currentBalance").asText()).isEqualTo("-12500.5000");
        assertThat(cardNode.path("creditLimit").asText()).isEqualTo("500000.0000");
        assertThat(cardNode.path("closingDay").asInt()).isEqualTo(24);
        assertThat(cardNode.path("dueDay").asInt()).isEqualTo(5);
    }

    @Test
    void visibilityRulesHideSharedFromChildInPersonalAndEnforceFamilyPolicy() {
        UUID familyId = fixtures.createFamily("Familia Vis " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-vis-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childOwnId = fixtures.createUser(
                familyId,
                "child-own-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID childFullId = fixtures.createUser(
                familyId,
                "child-full-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.FAMILY_FULL);

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childOwnBearer =
                tokens.bearer(childOwnId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        String childFullBearer =
                tokens.bearer(childFullId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);

        // Shared account (ownerUserId = null)
        client().post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Casa Compartida", "accountType", "BANK", "currencyCode", "ARS"))
                .retrieve()
                .toEntity(JsonNode.class);

        // Adult personal account
        client().post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Billetera Adulto",
                        "accountType", "WALLET",
                        "currencyCode", "ARS",
                        "ownerUserId", adultId.toString()))
                .retrieve()
                .toEntity(JsonNode.class);

        // Child account (child sends adultId as ownerUserId -> must become childOwnId!)
        ResponseEntity<JsonNode> childCreated = client()
                .post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, childOwnBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Ahorro Hijo",
                        "accountType", "SAVINGS",
                        "currencyCode", "ARS",
                        "ownerUserId", adultId.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(childCreated.getBody().path("ownerUserId").asText()).isEqualTo(childOwnId.toString());

        // Child in PERSONAL: sees ONLY own account (NOT shared, NOT adult's)
        ResponseEntity<JsonNode> childPersonal = client()
                .get()
                .uri("/api/v1/accounts?view=PERSONAL")
                .header(HttpHeaders.AUTHORIZATION, childOwnBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childPersonal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractNames(childPersonal.getBody())).containsExactly("Ahorro Hijo");

        // Child OWN_ONLY in FAMILY -> 403
        ResponseEntity<JsonNode> childOwnFamily = client()
                .get()
                .uri("/api/v1/accounts?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, childOwnBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childOwnFamily.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Child FAMILY_FULL in FAMILY -> sees all 3 family accounts
        ResponseEntity<JsonNode> childFullFamily = client()
                .get()
                .uri("/api/v1/accounts?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, childFullBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childFullFamily.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractNames(childFullFamily.getBody()))
                .containsExactly("Ahorro Hijo", "Billetera Adulto", "Casa Compartida");

        // Adult in PERSONAL -> sees own + shared (NOT child's)
        ResponseEntity<JsonNode> adultPersonal = client()
                .get()
                .uri("/api/v1/accounts?view=PERSONAL")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(adultPersonal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractNames(adultPersonal.getBody()))
                .containsExactly("Billetera Adulto", "Casa Compartida");
    }

    @Test
    void validationLifecycleSoftDeleteAndRequireUsableWorkEndToEnd() {
        UUID familyId = fixtures.createFamily("Familia Life " + UUID.randomUUID());
        UUID otherFamilyId = fixtures.createFamily("Familia Otra " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-life-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-life-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.FAMILY_FULL);
        UUID otherAdultId = fixtures.createUser(
                otherFamilyId,
                "other-adult-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);
        RestClient patchClient = patchCapableClient();

        // Credit card without closingDay -> 400
        ResponseEntity<JsonNode> missingClosing = client()
                .post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Master",
                        "accountType", "CREDIT_CARD",
                        "currencyCode", "ARS",
                        "dueDay", 10))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(missingClosing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Disabled currency -> 422
        ResponseEntity<JsonNode> disabledCurrency = client()
                .post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Euro Cash", "accountType", "CASH", "currencyCode", "EUR"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(disabledCurrency.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Create account owned by adult
        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Cuenta Sueldo",
                        "accountType", "BANK",
                        "currencyCode", "ARS",
                        "ownerUserId", adultId.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID accountId = UUID.fromString(created.getBody().path("id").asText());

        // Duplicate active name -> 409
        ResponseEntity<JsonNode> duplicate = client()
                .post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "cuenta sueldo", "accountType", "CASH", "currencyCode", "ARS"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // PATCH by adult: convert to shared (explicit ownerUserId: null) and rename
        Map<String, Object> patchPayload = new HashMap<>();
        patchPayload.put("name", "Cuenta Familiar");
        patchPayload.put("ownerUserId", null);
        patchPayload.put("currencyCode", "USD"); // ignored, currencyCode cannot change
        ResponseEntity<JsonNode> patched = patchClient
                .patch()
                .uri("/api/v1/accounts/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchPayload)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().path("name").asText()).isEqualTo("Cuenta Familiar");
        assertThat(patched.getBody().path("ownerUserId").isNull()).isTrue();
        assertThat(patched.getBody().path("currencyCode").asText()).isEqualTo("ARS");

        // AccountQueryApi.requireUsable:
        CurrentUser otherAdult =
                new CurrentUser(otherAdultId, otherFamilyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        CurrentUser childUser =
                new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);
        CurrentUser adultUser =
                new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        // Other family -> 404
        assertThatThrownBy(() -> accountQueryApi.requireUsable(otherAdult, accountId))
                .isInstanceOf(NotFoundException.class);
        // HIJO on shared account -> 403
        assertThatThrownBy(() -> accountQueryApi.requireUsable(childUser, accountId))
                .isInstanceOf(ForbiddenException.class);
        // ADULTO on shared account -> OK
        AccountRef ref = accountQueryApi.requireUsable(adultUser, accountId);
        assertThat(ref.id()).isEqualTo(accountId);
        assertThat(ref.name()).isEqualTo("Cuenta Familiar");
        assertThat(ref.ownerUserId()).isNull();

        // HIJO cannot delete shared account -> 403
        ResponseEntity<JsonNode> childDelete = client()
                .delete()
                .uri("/api/v1/accounts/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Insert historical transaction before soft-delete
        insertTransaction(familyId, adultId, accountId, "INCOME", "POSTED", "2500.0000", null);

        // ADULTO soft-deletes account -> 204
        ResponseEntity<Void> deleted = client()
                .delete()
                .uri("/api/v1/accounts/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Historical transaction remains intact
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?", Integer.class, accountId);
        assertThat(txCount).isEqualTo(1);

        // Excluded when includeInactive=false, included when includeInactive=true
        ResponseEntity<JsonNode> activeOnly = client()
                .get()
                .uri("/api/v1/accounts?view=FAMILY&includeInactive=false")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(activeOnly.getBody()).isEmpty();

        ResponseEntity<JsonNode> withInactive = client()
                .get()
                .uri("/api/v1/accounts?view=FAMILY&includeInactive=true")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(withInactive.getBody()).hasSize(1);
        assertThat(withInactive.getBody().get(0).path("isActive").asBoolean()).isFalse();
        assertThat(withInactive.getBody().get(0).path("currentBalance").asText()).isEqualTo("2500.0000");

        // requireUsable rejects soft-deleted account with 404
        assertThatThrownBy(() -> accountQueryApi.requireUsable(adultUser, accountId))
                .isInstanceOf(NotFoundException.class);
    }

    private void insertTransaction(
            UUID familyId,
            UUID userId,
            UUID accountId,
            String type,
            String status,
            String amount,
            Instant deletedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id,
                    type, scope, status, amount, currency_code, transaction_date, deleted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'PERSONAL', ?, ?, 'ARS', ?, ?)
                """,
                UUID.randomUUID(),
                familyId,
                userId,
                userId,
                accountId,
                type,
                status,
                new BigDecimal(amount),
                LocalDate.of(2026, 6, 1),
                deletedAt == null ? null : java.sql.Timestamp.from(deletedAt));
    }

    private static JsonNode findById(JsonNode array, UUID id) {
        for (JsonNode node : array) {
            if (id.toString().equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("Account not found in response: " + id);
    }

    private static List<String> extractNames(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(node -> names.add(node.path("name").asText()));
        return names;
    }
}
