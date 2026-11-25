package com.familion.analytics.exports.infrastructure.format;

import com.familion.analytics.exports.application.ExportTransactionLine;
import com.familion.analytics.exports.application.ExportWriter;
import com.familion.analytics.exports.application.MonthlySummaryRow;
import com.familion.analytics.exports.domain.CsvCellFormatter;
import com.familion.analytics.exports.domain.ExportFormat;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CsvExportWriter implements ExportWriter {

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public void writeTransactions(OutputStream out, String lensUnit, TransactionRowSource rowSource)
            throws IOException {
        out.write(CsvCellFormatter.UTF8_BOM);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writeRow(
                writer,
                CsvCellFormatter.formatText("Fecha"),
                CsvCellFormatter.formatText("Tipo"),
                CsvCellFormatter.formatText("Descripción"),
                CsvCellFormatter.formatText("Categoría"),
                CsvCellFormatter.formatText("Cuenta"),
                CsvCellFormatter.formatText("Pagado por"),
                CsvCellFormatter.formatText("Atribuido a"),
                CsvCellFormatter.formatText("Moneda"),
                CsvCellFormatter.formatText("Monto"),
                CsvCellFormatter.formatText("Monto (" + lensUnit + ")"),
                CsvCellFormatter.formatText("Cuota"),
                CsvCellFormatter.formatText("Estado"));

        rowSource.streamTo(line -> writeTransactionLine(writer, line));

        writer.flush();
    }

    @Override
    public void writeMonthlySummary(OutputStream out, List<MonthlySummaryRow> rows)
            throws IOException {
        out.write(CsvCellFormatter.UTF8_BOM);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writeRow(
                writer,
                CsvCellFormatter.formatText("Mes"),
                CsvCellFormatter.formatText("Ingresos"),
                CsvCellFormatter.formatText("Gastos"),
                CsvCellFormatter.formatText("Balance"),
                CsvCellFormatter.formatText("Tasa de ahorro %"));

        for (MonthlySummaryRow row : rows) {
            writeRow(
                    writer,
                    CsvCellFormatter.formatText(row.label()),
                    CsvCellFormatter.formatDecimal(row.income()),
                    CsvCellFormatter.formatDecimal(row.expense()),
                    CsvCellFormatter.formatDecimal(row.balance()),
                    CsvCellFormatter.formatDecimal(row.savingsRatePct()));
        }

        writer.flush();
    }

    private static void writeTransactionLine(BufferedWriter writer, ExportTransactionLine line)
            throws IOException {
        writeRow(
                writer,
                CsvCellFormatter.formatDate(line.date()),
                CsvCellFormatter.formatText(line.typeLabel()),
                CsvCellFormatter.formatText(line.description()),
                CsvCellFormatter.formatText(line.categoryName()),
                CsvCellFormatter.formatText(line.accountName()),
                CsvCellFormatter.formatText(line.paidByName()),
                CsvCellFormatter.formatText(line.attributedToName()),
                CsvCellFormatter.formatText(line.currencyCode()),
                CsvCellFormatter.formatDecimal(line.amount()),
                CsvCellFormatter.formatDecimal(line.lensAmount()),
                CsvCellFormatter.formatText(line.installmentLabel()),
                CsvCellFormatter.formatText(line.statusLabel()));
    }

    private static void writeRow(BufferedWriter writer, String... cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                writer.write(CsvCellFormatter.SEPARATOR);
            }
            writer.write(cells[i]);
        }
        writer.write(CsvCellFormatter.LINE_ENDING);
    }
}
