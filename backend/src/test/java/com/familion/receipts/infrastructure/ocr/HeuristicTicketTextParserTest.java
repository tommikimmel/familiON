package com.familion.receipts.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.receipts.application.port.ReceiptDraft;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeuristicTicketTextParserTest {

    private final HeuristicTicketTextParser parser = new HeuristicTicketTextParser();

    @Test
    void parsesSupermarketTicketFixture() throws IOException {
        String text = readFixture("receipts/supermarket.txt");

        ReceiptDraft draft = parser.parse(text);

        assertThat(draft.merchant()).isEqualTo("COTO C.I.C.S.A.");
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(draft.totalAmount()).isEqualByComparingTo(new BigDecimal("38750.5000"));
        assertThat(draft.totalAmount().scale()).isEqualTo(4);
        assertThat(draft.currencyCode()).isEqualTo("ARS");
        assertThat(draft.suggestedCategoryKey()).isEqualTo("SUPERMARKET");
        assertThat(draft.confidence()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(draft.rawText()).isEqualTo(text);
    }

    @Test
    void parsesFuelStationTicketFixtureWithShortDateAndDotDecimalTotal() throws IOException {
        String text = readFixture("receipts/fuel-station.txt");

        ReceiptDraft draft = parser.parse(text);

        assertThat(draft.merchant()).isEqualTo("YPF S.A. - ESTACION AV. LIBERTADOR");
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(draft.totalAmount()).isEqualByComparingTo(new BigDecimal("54320.7500"));
        assertThat(draft.currencyCode()).isEqualTo("ARS");
        assertThat(draft.suggestedCategoryKey()).isEqualTo("VEHICLES_FUEL");
        assertThat(draft.confidence()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(draft.rawText()).isEqualTo(text);
    }

    @Test
    void parsesPharmacyTicketFixtureWithUsdCurrency() throws IOException {
        String text = readFixture("receipts/pharmacy.txt");

        ReceiptDraft draft = parser.parse(text);

        assertThat(draft.merchant()).isEqualTo("FARMACIA DEL PUEBLO");
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 5, 22));
        assertThat(draft.totalAmount()).isEqualByComparingTo(new BigDecimal("45.8000"));
        assertThat(draft.currencyCode()).isEqualTo("USD");
        assertThat(draft.suggestedCategoryKey()).isEqualTo("HEALTH");
        assertThat(draft.confidence()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(draft.rawText()).isEqualTo(text);
    }

    @Test
    void handlesUnreadableTicketWithZeroConfidence() throws IOException {
        String text = readFixture("receipts/unreadable.txt");

        ReceiptDraft draft = parser.parse(text);

        assertThat(draft.merchant()).isNull();
        assertThat(draft.date()).isNull();
        assertThat(draft.totalAmount()).isNull();
        assertThat(draft.currencyCode()).isEqualTo("ARS");
        assertThat(draft.suggestedCategoryKey()).isEqualTo("OTHER_EXPENSE");
        assertThat(draft.confidence()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(draft.rawText()).isEqualTo(text);
    }

    @Test
    void picksLastTotalMatchAndDetectsUsdKeyword() {
        String text = """
                TIQUE NO FISCAL
                PANADERIA LA ESPIGA
                01/02/2026
                TOTAL PARCIAL 1.000,00
                TOTAL FINAL USD 1.234,56
                """;

        ReceiptDraft draft = parser.parse(text);

        assertThat(draft.merchant()).isEqualTo("PANADERIA LA ESPIGA");
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(draft.totalAmount()).isEqualByComparingTo(new BigDecimal("1234.5600"));
        assertThat(draft.currencyCode()).isEqualTo("USD");
        assertThat(draft.suggestedCategoryKey()).isEqualTo("OTHER_EXPENSE");
        assertThat(draft.confidence()).isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @ParameterizedTest
    @CsvSource({
        "SHELL ARGENTINA, VEHICLES_FUEL",
        "AXION ENERGY, VEHICLES_FUEL",
        "PUMA ENERGY, VEHICLES_FUEL",
        "SUPERMERCADO DIA %, SUPERMARKET",
        "CARREFOUR EXPRESS, SUPERMARKET",
        "JUMBO PALERMO, SUPERMARKET",
        "DISCO BELGRANO, SUPERMARKET",
        "CHANGOMAS SUR, SUPERMARKET",
        "FARMACIAS DR AHORRO, HEALTH",
        "FERRETERIA EL TORNILLO, OTHER_EXPENSE"
    })
    void mapsCategoryKeywordsToSystemKeys(String merchantLine, String expectedCategoryKey) {
        ReceiptDraft draft = parser.parse(merchantLine + "\nTOTAL 100.00\n10/01/2026");

        assertThat(draft.suggestedCategoryKey()).isEqualTo(expectedCategoryKey);
    }

    @Test
    void skipsInvalidCalendarDatesAndPicksNextValidDate() {
        String text = """
                KIOSCO CENTRAL
                31/02/2026
                15-06-26
                TOTAL 1500
                """;

        ReceiptDraft draft = parser.parse(text);

        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(draft.totalAmount()).isEqualByComparingTo(new BigDecimal("1500.0000"));
    }

    private static String readFixture(String resourcePath) throws IOException {
        try (InputStream in =
                HeuristicTicketTextParserTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("fixture %s must exist", resourcePath).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
