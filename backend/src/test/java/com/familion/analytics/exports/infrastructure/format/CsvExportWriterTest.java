package com.familion.analytics.exports.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.analytics.exports.application.ExportTransactionLine;
import com.familion.analytics.exports.application.MonthlySummaryRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExportWriterTest {

    private final CsvExportWriter writer = new CsvExportWriter();

    @Test
    void writesUtf8BomSemicolonCommaDecimalsEscapingAndCsvInjectionProtection() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ExportTransactionLine line1 = new ExportTransactionLine(
                LocalDate.of(2026, 11, 5),
                "Gasto",
                "Super \"Central\"; promos",
                "Alimentación",
                "Banco Galicia",
                "Ana Pérez",
                "Ana Pérez",
                "ARS",
                new BigDecimal("15250.5000"),
                new BigDecimal("15250.5000"),
                "2/6",
                "Confirmado");

        ExportTransactionLine line2 = new ExportTransactionLine(
                LocalDate.of(2026, 11, 10),
                "Gasto",
                "=cmd|'/C calc'!A0",
                null,
                "Efectivo",
                "Ana Pérez",
                "Lucas Pérez",
                "USD",
                new BigDecimal("12.3400"),
                new BigDecimal("14808.0000"),
                "",
                "Proyectado");

        writer.writeTransactions(out, "ARS (hoy)", sink -> {
            sink.accept(line1);
            sink.accept(line2);
        });

        byte[] bytes = out.toByteArray();
        assertThat(Arrays.copyOfRange(bytes, 0, 3))
                .containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        String[] lines = content.split("\r\n");

        assertThat(lines).hasSize(3);
        assertThat(lines[0])
                .isEqualTo(
                        "Fecha;Tipo;Descripción;Categoría;Cuenta;Pagado por;Atribuido a;Moneda;Monto;Monto (ARS (hoy));Cuota;Estado");
        assertThat(lines[1])
                .isEqualTo(
                        "05/11/2026;Gasto;\"Super \"\"Central\"\"; promos\";Alimentación;Banco Galicia;Ana Pérez;Ana Pérez;ARS;15250,50;15250,50;2/6;Confirmado");
        assertThat(lines[2])
                .isEqualTo(
                        "10/11/2026;Gasto;'=cmd|'/C calc'!A0;;Efectivo;Ana Pérez;Lucas Pérez;USD;12,34;14808,00;;Proyectado");
    }

    @Test
    void writesMonthlySummaryWithBomCommaDecimalsAndNegativeNumbersPreserved() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<MonthlySummaryRow> rows = List.of(
                new MonthlySummaryRow(
                        "Enero",
                        new BigDecimal("100000.0000"),
                        new BigDecimal("40000.0000"),
                        new BigDecimal("60000.0000"),
                        new BigDecimal("60.0000"),
                        false),
                new MonthlySummaryRow(
                        "Febrero",
                        new BigDecimal("50000.0000"),
                        new BigDecimal("62500.0000"),
                        new BigDecimal("-12500.0000"),
                        new BigDecimal("-25.0000"),
                        false),
                new MonthlySummaryRow(
                        "Marzo",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        false),
                new MonthlySummaryRow(
                        "Total",
                        new BigDecimal("150000.0000"),
                        new BigDecimal("102500.0000"),
                        new BigDecimal("47500.0000"),
                        new BigDecimal("31.6667"),
                        true));

        writer.writeMonthlySummary(out, rows);

        byte[] bytes = out.toByteArray();
        assertThat(Arrays.copyOfRange(bytes, 0, 3))
                .containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        String[] lines = content.split("\r\n");

        assertThat(lines).hasSize(5);
        assertThat(lines[0]).isEqualTo("Mes;Ingresos;Gastos;Balance;Tasa de ahorro %");
        assertThat(lines[1]).isEqualTo("Enero;100000,00;40000,00;60000,00;60,00");
        assertThat(lines[2]).isEqualTo("Febrero;50000,00;62500,00;-12500,00;-25,00");
        assertThat(lines[3]).isEqualTo("Marzo;0,00;0,00;0,00;");
        assertThat(lines[4]).isEqualTo("Total;150000,00;102500,00;47500,00;31,67");
    }
}
