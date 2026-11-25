package com.familion.analytics.exports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvCellFormatterTest {

    @Test
    void utf8BomMatchesStandardThreeBytes() {
        assertThat(CsvCellFormatter.UTF8_BOM)
                .containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void formatTextReturnsEmptyForNullOrEmpty() {
        assertThat(CsvCellFormatter.formatText(null)).isEmpty();
        assertThat(CsvCellFormatter.formatText("")).isEmpty();
    }

    @Test
    void formatTextLeavesSafePlainTextUntouched() {
        assertThat(CsvCellFormatter.formatText("Supermercado Día")).isEqualTo("Supermercado Día");
    }

    @Test
    void formatTextQuotesAndEscapesSemicolonQuotesAndNewlines() {
        assertThat(CsvCellFormatter.formatText("Pan; leche"))
                .isEqualTo("\"Pan; leche\"");
        assertThat(CsvCellFormatter.formatText("Supermercado \"La Reina\""))
                .isEqualTo("\"Supermercado \"\"La Reina\"\"\"");
        assertThat(CsvCellFormatter.formatText("Línea 1\nLínea 2"))
                .isEqualTo("\"Línea 1\nLínea 2\"");
        assertThat(CsvCellFormatter.formatText("Super \"A\"; sucursal 2"))
                .isEqualTo("\"Super \"\"A\"\"; sucursal 2\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=cmd|'/C calc'!A0", "+2+3", "-2+3", "@SUM(1,2)"})
    void formatTextPrefixesApostropheForCsvInjectionPayloads(String payload) {
        assertThat(CsvCellFormatter.formatText(payload)).isEqualTo("'" + payload);
    }

    @Test
    void formatTextSanitizesAndQuotesFormulaContainingSemicolonOrQuotes() {
        assertThat(CsvCellFormatter.formatText("=HYPERLINK(\"http://evil\";\"click\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\";\"\"click\"\")\"");
    }

    @Test
    void formatDecimalUsesCommaAndHalfEvenRoundingWithoutThousandsSeparator() {
        assertThat(CsvCellFormatter.formatDecimal(null)).isEmpty();
        assertThat(CsvCellFormatter.formatDecimal(new BigDecimal("15000.0000")))
                .isEqualTo("15000,00");
        assertThat(CsvCellFormatter.formatDecimal(new BigDecimal("1234.5678")))
                .isEqualTo("1234,57");
        assertThat(CsvCellFormatter.formatDecimal(new BigDecimal("10.005")))
                .isEqualTo("10,00");
        assertThat(CsvCellFormatter.formatDecimal(new BigDecimal("-500.5")))
                .isEqualTo("-500,50");
    }

    @Test
    void formatDateUsesDdMmYyyy() {
        assertThat(CsvCellFormatter.formatDate(null)).isEmpty();
        assertThat(CsvCellFormatter.formatDate(LocalDate.of(2026, 11, 5)))
                .isEqualTo("05/11/2026");
    }
}
