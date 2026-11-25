package com.familion.analytics.exports.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.analytics.exports.application.ExportTransactionLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
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
}
