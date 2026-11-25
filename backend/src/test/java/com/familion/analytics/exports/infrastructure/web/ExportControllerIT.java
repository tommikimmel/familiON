package com.familion.analytics.exports.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.analytics.exports.infrastructure.format.XlsxExportWriter;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class ExportControllerIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID familyId;
    private UUID adultId;
    private UUID childFullId;
    private UUID childSummaryId;
    private UUID childOwnId;

    private UUID adultAccountId;
    private UUID childAccountId;
    private UUID foodCategoryId;

    private String adultBearer;
    private String childFullBearer;
    private String childSummaryBearer;
    private String childOwnBearer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM economic_indices");
        clearRedisKeys("econ:*");
        clearRedisKeys("family:*:analytics:*");
        seedDataset();
    }

    @Test
    void exportsTransactionsCsvWithBomSemicolonCommaDecimalsEscapingAndCsvInjectionGuard() {
        ResponseEntity<byte[]> response = getBytes(
                "/api/v1/exports/transactions?format=CSV&from=2026-01-01&to=2026-11-30&view=FAMILY&lens=CONSTANT_BASE",
                adultBearer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(
                        MediaType.parseMediaType("text/csv")))
                .isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"familion-movimientos-2026-01-01_2026-11-30.csv\"");

        byte[] body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(Arrays.copyOfRange(body, 0, 3))
                .containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        // Header + 6 active (POSTED/PROJECTED) transactions; deleted & cancelled excluded
        assertThat(lines).hasSize(7);
        assertThat(lines[0])
                .isEqualTo(
                        "Fecha;Tipo;Descripción;Categoría;Cuenta;Pagado por;Atribuido a;Moneda;Monto;Monto (ARS (hoy));Cuota;Estado");

        // Verify CSV injection protection, semicolon/quote escaping, installment, IPC adjustment, Spanish types
        assertThat(csv)
                .contains(
                        "20/11/2026;Gasto;'=cmd|'/C calc'!A0;Alimentación;Banco Principal;Ana Adulta;Ana Adulta;ARS;15000,00;15000,00;1/3;Proyectado");
        assertThat(csv)
                .contains(
                        "18/11/2026;Gasto;\"Super \"\"Norte\"\"; promo\";Alimentación;Banco Principal;Ana Adulta;Lauti Full;ARS;20000,00;20000,00;;Confirmado");
        // Oct 2026 expense 50000 ARS at IPC 110 -> latest IPC 121 => 55000,00
        assertThat(csv)
                .contains(
                        "12/10/2026;Gasto;Compra Octubre;Alimentación;Banco Principal;Ana Adulta;Ana Adulta;ARS;50000,00;55000,00;;Confirmado");
        assertThat(csv).contains("Mensualidad (recibida)");
        assertThat(csv).contains("Transferencia (salida)");
        assertThat(csv).doesNotContain("Deleted Tx");
        assertThat(csv).doesNotContain("Cancelled Tx");
    }

    @Test
    void exportsTransactionsXlsxAndVerifiesWithPoi() throws Exception {
        ResponseEntity<byte[]> response = getBytes(
                "/api/v1/exports/transactions?format=XLSX&from=2026-01-01&to=2026-11-30&view=FAMILY&lens=REFERENCE_CURRENCY",
                adultBearer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"familion-movimientos-2026-01-01_2026-11-30.xlsx\"");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))) {
            XSSFSheet sheet = wb.getSheet(XlsxExportWriter.TRANSACTIONS_SHEET_NAME);
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(7);
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Fecha");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("Monto (USD)");
            assertThat(wb.getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold())
                    .isTrue();

            Row firstDataRow = sheet.getRow(1);
            assertThat(DateUtil.isCellDateFormatted(firstDataRow.getCell(0))).isTrue();
            assertThat(firstDataRow.getCell(8).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(firstDataRow.getCell(8).getCellStyle().getDataFormatString())
                    .isEqualTo(XlsxExportWriter.NUMBER_FORMAT);
            assertThat(firstDataRow.getCell(9).getCellType()).isEqualTo(CellType.NUMERIC);
        }
    }

    @Test
    void enforcesPersonalAttributionVisibilityOnTransactionsExport() {
        ResponseEntity<byte[]> childPersonal = getBytes(
                "/api/v1/exports/transactions?format=CSV&from=2026-01-01&to=2026-11-30&view=PERSONAL&lens=NOMINAL",
                childFullBearer);

        assertThat(childPersonal.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = new String(
                childPersonal.getBody(), 3, childPersonal.getBody().length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        // Child attributed transactions: "Super \"Norte\"; promo" + "Mensualidad Lauti"
        assertThat(lines).hasSize(3);
        assertThat(csv).contains("Super \"\"Norte\"\"; promo");
        assertThat(csv).contains("Mensualidad Lauti");
        assertThat(csv).doesNotContain("Sueldo Noviembre");
    }

    @Test
    void exportsMonthlySummaryCsvAndXlsxRespectingResolveForTotals() throws Exception {
        // FAMILY_SUMMARY child CAN export monthly summary in FAMILY view
        ResponseEntity<byte[]> csvResp = getBytes(
                "/api/v1/exports/monthly-summary?format=CSV&year=2026&view=FAMILY&lens=NOMINAL",
                childSummaryBearer);

        assertThat(csvResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csvResp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"familion-resumen-mensual-2026.csv\"");

        String csv = new String(
                csvResp.getBody(), 3, csvResp.getBody().length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(14); // header + 12 months + Total
        assertThat(lines[0]).isEqualTo("Mes;Ingresos;Gastos;Balance;Tasa de ahorro %");
        // Oct: 0 income, 50000 expense -> balance -50000, empty savings rate
        assertThat(lines[10]).isEqualTo("Octubre;0,00;50000,00;-50000,00;");
        // Nov: 200000 income (ALLOWANCE_IN excluded in FAMILY!), 20000 POSTED expense (PROJECTED excluded)
        assertThat(lines[11]).isEqualTo("Noviembre;200000,00;20000,00;180000,00;90,00");
        // Total: 200000 income, 70000 expense, 130000 balance, 65% savings rate
        assertThat(lines[13]).isEqualTo("Total;200000,00;70000,00;130000,00;65,00");

        // XLSX monthly summary with CONSTANT_BASE
        ResponseEntity<byte[]> xlsxResp = getBytes(
                "/api/v1/exports/monthly-summary?format=XLSX&year=2026&view=FAMILY&lens=CONSTANT_BASE",
                adultBearer);
        assertThat(xlsxResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xlsxResp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"familion-resumen-mensual-2026.xlsx\"");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxResp.getBody()))) {
            XSSFSheet sheet = wb.getSheet(XlsxExportWriter.SUMMARY_SHEET_NAME);
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(14);
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEqualTo("Octubre");
            // Oct expense adjusted 50000 * 121 / 110 = 55000
            assertThat(sheet.getRow(10).getCell(2).getNumericCellValue()).isEqualTo(55000.0);
            // Total expense = 55000 + 20000 = 75000, balance = 125000, savings rate = 62.5%
            Row totalRow = sheet.getRow(13);
            assertThat(totalRow.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(totalRow.getCell(2).getNumericCellValue()).isEqualTo(75000.0);
            assertThat(totalRow.getCell(3).getNumericCellValue()).isEqualTo(125000.0);
            assertThat(totalRow.getCell(4).getNumericCellValue()).isEqualTo(62.5);
        }
    }

    @Test
    void exportsTransactionsWithIncomePctLensUsingFullMonthIncome() {
        // Request a mid-month slice (Nov 10..Nov 20); Nov income was posted on Nov 1 (200000 ARS)
        ResponseEntity<byte[]> response = getBytes(
                "/api/v1/exports/transactions?format=CSV&from=2026-11-10&to=2026-11-20&view=FAMILY&lens=INCOME_PCT",
                adultBearer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = new String(
                response.getBody(), 3, response.getBody().length - 3, StandardCharsets.UTF_8);
        assertThat(csv).contains("Monto (%)");
        // 20000 / 200000 = 10,00%
        assertThat(csv).contains("20000,00;10,00");
        // 15000 / 200000 = 7,50%
        assertThat(csv).contains("15000,00;7,50");
    }

    @Test
    void returns403ForUnauthorizedVisibilityAnd400ForInvalidRangeOrYear() {
        // HIJO OWN_ONLY + FAMILY -> 403 on transactions
        ResponseEntity<JsonNode> txForbiddenOwn = getProblem(
                "/api/v1/exports/transactions?format=CSV&from=2026-01-01&to=2026-11-30&view=FAMILY&lens=NOMINAL",
                childOwnBearer);
        assertThat(txForbiddenOwn.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(txForbiddenOwn.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        // HIJO FAMILY_SUMMARY + FAMILY -> 403 on transactions listing
        ResponseEntity<JsonNode> txForbiddenSummary = getProblem(
                "/api/v1/exports/transactions?format=CSV&from=2026-01-01&to=2026-11-30&view=FAMILY&lens=NOMINAL",
                childSummaryBearer);
        assertThat(txForbiddenSummary.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // HIJO OWN_ONLY + FAMILY -> 403 on monthly-summary
        ResponseEntity<JsonNode> summaryForbiddenOwn = getProblem(
                "/api/v1/exports/monthly-summary?format=CSV&year=2026&view=FAMILY&lens=NOMINAL",
                childOwnBearer);
        assertThat(summaryForbiddenOwn.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Range > 3 years -> 400
        ResponseEntity<JsonNode> tooLongRange = getProblem(
                "/api/v1/exports/transactions?format=CSV&from=2023-01-01&to=2026-01-02&view=PERSONAL&lens=NOMINAL",
                adultBearer);
        assertThat(tooLongRange.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLongRange.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(tooLongRange.getBody().path("detail").asText()).contains("3 years");

        // Invalid year -> 400
        ResponseEntity<JsonNode> invalidYear = getProblem(
                "/api/v1/exports/monthly-summary?format=CSV&year=1800&view=PERSONAL&lens=NOMINAL",
                adultBearer);
        assertThat(invalidYear.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Unauthenticated -> 401
        ResponseEntity<JsonNode> unauth = client()
                .get()
                .uri("/api/v1/exports/transactions?format=CSV&from=2026-01-01&to=2026-11-30")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void seedDataset() {
        familyId = fixtures.createFamily("Familia Exports");
        adultId = createNamedUser(familyId, "Ana Adulta", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childFullId = createNamedUser(familyId, "Lauti Full", Role.HIJO, FamilyVisibility.FAMILY_FULL);
        childSummaryId =
                createNamedUser(familyId, "Sofi Summary", Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        childOwnId = createNamedUser(familyId, "Mili Own", Role.HIJO, FamilyVisibility.OWN_ONLY);

        adultBearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childFullBearer =
                tokens.bearer(childFullId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);
        childSummaryBearer =
                tokens.bearer(childSummaryId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        childOwnBearer =
                tokens.bearer(childOwnId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        adultAccountId = createNamedAccount(familyId, adultId, "Banco Principal", "BANK", "ARS");
        childAccountId = createNamedAccount(familyId, childFullId, "Billetera Lauti", "CASH", "ARS");
        foodCategoryId = createCategory(familyId, "Alimentación");

        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 10, 1), new BigDecimal("110.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("121.000000"));

        // 1. Oct expense (IPC=110 -> 55000 in CONSTANT_BASE)
        insertTx(
                adultId,
                adultId,
                adultAccountId,
                foodCategoryId,
                "EXPENSE",
                "POSTED",
                "50000.0000",
                "ARS",
                LocalDate.of(2026, 10, 12),
                "Compra Octubre",
                "50000.0000",
                "50.0000",
                "110.000000",
                null,
                null,
                false);

        // 2. Nov income
        insertTx(
                adultId,
                adultId,
                adultAccountId,
                null,
                "INCOME",
                "POSTED",
                "200000.0000",
                "ARS",
                LocalDate.of(2026, 11, 1),
                "Sueldo Noviembre",
                "200000.0000",
                "200.0000",
                "121.000000",
                null,
                null,
                false);

        // 3. Nov allowance IN to child
        insertTx(
                adultId,
                childFullId,
                childAccountId,
                null,
                "ALLOWANCE_IN",
                "POSTED",
                "30000.0000",
                "ARS",
                LocalDate.of(2026, 11, 5),
                "Mensualidad Lauti",
                "30000.0000",
                "30.0000",
                "121.000000",
                null,
                null,
                false);

        // 4. Nov transfer OUT
        insertTx(
                adultId,
                adultId,
                adultAccountId,
                null,
                "TRANSFER_OUT",
                "POSTED",
                "10000.0000",
                "ARS",
                LocalDate.of(2026, 11, 8),
                "Ahorro plazo fijo",
                "10000.0000",
                "10.0000",
                "121.000000",
                null,
                null,
                false);

        // 5. Nov expense paid by adult, attributed to child, with quotes & semicolon
        insertTx(
                adultId,
                childFullId,
                adultAccountId,
                foodCategoryId,
                "EXPENSE",
                "POSTED",
                "20000.0000",
                "ARS",
                LocalDate.of(2026, 11, 18),
                "Super \"Norte\"; promo",
                "20000.0000",
                "20.0000",
                "121.000000",
                null,
                null,
                false);

        // 6. Nov projected installment with CSV injection payload
        UUID planId = insertInstallmentPlan(adultId, adultAccountId, foodCategoryId, "Cuota test", "15000.0000");
        insertTx(
                adultId,
                adultId,
                adultAccountId,
                foodCategoryId,
                "EXPENSE",
                "PROJECTED",
                "15000.0000",
                "ARS",
                LocalDate.of(2026, 11, 20),
                "=cmd|'/C calc'!A0",
                "15000.0000",
                "15.0000",
                "121.000000",
                planId,
                1,
                false);

        // Excluded: soft-deleted
        insertTx(
                adultId,
                adultId,
                adultAccountId,
                foodCategoryId,
                "EXPENSE",
                "POSTED",
                "99999.0000",
                "ARS",
                LocalDate.of(2026, 11, 15),
                "Deleted Tx",
                "99999.0000",
                "99.9990",
                "121.000000",
                null,
                null,
                true);

        // Excluded: CANCELLED
        insertTx(
                adultId,
                adultId,
                adultAccountId,
                foodCategoryId,
                "EXPENSE",
                "CANCELLED",
                "88888.0000",
                "ARS",
                LocalDate.of(2026, 11, 16),
                "Cancelled Tx",
                "88888.0000",
                "88.8880",
                "121.000000",
                null,
                null,
                false);
    }

    private UUID createNamedUser(UUID familyId, String fullName, Role role, FamilyVisibility vis) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, family_id, email, password_hash, full_name, role, family_visibility,
                    accent_color, email_verified_at, two_factor_enabled, is_active
                ) VALUES (?, ?, ?, '$2a$10$testdummyhash000000000000000000000000000000000000000000',
                          ?, ?, ?, '#2563EB', NOW(), FALSE, TRUE)
                """,
                userId,
                familyId,
                "export-" + UUID.randomUUID() + "@familion.test",
                fullName,
                role.name(),
                vis.name());
        return userId;
    }

    private UUID createNamedAccount(
            UUID familyId, UUID ownerUserId, String name, String type, String currency) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    id, family_id, owner_user_id, name, account_type, currency_code,
                    initial_balance, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, 0, TRUE)
                """,
                id,
                familyId,
                ownerUserId,
                name,
                type,
                currency);
        return id;
    }

    private UUID createCategory(UUID familyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO categories (id, family_id, name, kind, color_hex, icon, is_system, is_active)
                VALUES (?, ?, ?, 'EXPENSE', '#10B981', 'utensils', FALSE, TRUE)
                """,
                id,
                familyId,
                name);
        return id;
    }

    private void insertIndex(String code, LocalDate date, BigDecimal value) {
        jdbcTemplate.update(
                """
                INSERT INTO economic_indices (id, index_code, effective_date, value, source)
                VALUES (?, ?, ?, ?, 'MANUAL')
                """,
                UUID.randomUUID(),
                code,
                date,
                value);
    }

    private UUID insertInstallmentPlan(
            UUID userId, UUID accountId, UUID categoryId, String description, String amount) {
        UUID planId = UUID.randomUUID();
        BigDecimal inst = new BigDecimal(amount);
        jdbcTemplate.update(
                """
                INSERT INTO installment_plans (
                    id, family_id, user_id, credit_card_account_id, category_id, description,
                    currency_code, total_installments, has_interest, financed_total,
                    installment_amount, purchase_date, first_billing_date, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'ARS', 3, FALSE, ?, ?, '2026-10-15', '2026-11-20', 'ACTIVE')
                """,
                planId,
                familyId,
                userId,
                accountId,
                categoryId,
                description,
                inst.multiply(BigDecimal.valueOf(3)),
                inst);
        return planId;
    }

    private void insertTx(
            UUID userId,
            UUID attributedToUserId,
            UUID accountId,
            UUID categoryId,
            String type,
            String status,
            String amount,
            String currency,
            LocalDate date,
            String description,
            String baseAmount,
            String refAmount,
            String ipc,
            UUID planId,
            Integer installmentNumber,
            boolean deleted) {
        UUID txId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id, category_id,
                    type, scope, status, amount, currency_code, transaction_date, description,
                    installment_plan_id, installment_number, deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PERSONAL', ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                txId,
                familyId,
                userId,
                attributedToUserId,
                accountId,
                categoryId,
                type,
                status,
                new BigDecimal(amount),
                currency,
                date,
                description,
                planId,
                installmentNumber,
                deleted ? java.time.OffsetDateTime.now() : null);

        jdbcTemplate.update(
                """
                INSERT INTO economic_snapshots (
                    transaction_id, reference_currency, reference_rate_source, reference_exchange_rate,
                    amount_in_reference, base_currency_amount, ipc_index_value, snapshot_date
                ) VALUES (?, 'USD', 'USD_MEP', 1000.000000, ?, ?, ?, ?)
                """,
                txId,
                new BigDecimal(refAmount),
                new BigDecimal(baseAmount),
                new BigDecimal(ipc),
                date);
    }

    private ResponseEntity<byte[]> getBytes(String uri, String bearer) {
        return client()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(byte[].class);
    }

    private ResponseEntity<JsonNode> getProblem(String uri, String bearer) {
        return client()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private void clearRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
