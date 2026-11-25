package com.familion.analytics.exports.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.analytics.exports.application.ExportTransactionLine;
import com.familion.analytics.exports.application.MonthlySummaryRow;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class XlsxExportWriterTest {

    private final XlsxExportWriter writer = new XlsxExportWriter();

    @Test
    void writesStreamingTransactionsWorkbookWithHeadersBoldFrozenDatesAndNumericFormats()
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ExportTransactionLine line1 = new ExportTransactionLine(
                LocalDate.of(2026, 11, 5),
                "Gasto",
                "Supermercado",
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
                null,
                "",
                "Proyectado");

        writer.writeTransactions(out, "USD", sink -> {
            sink.accept(line1);
            sink.accept(line2);
        });

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            XSSFSheet sheet = wb.getSheet(XlsxExportWriter.TRANSACTIONS_SHEET_NAME);
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(sheet.getPaneInformation()).isNotNull();
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);

            Row header = sheet.getRow(0);
            String[] expectedHeaders = {
                "Fecha",
                "Tipo",
                "Descripción",
                "Categoría",
                "Cuenta",
                "Pagado por",
                "Atribuido a",
                "Moneda",
                "Monto",
                "Monto (USD)",
                "Cuota",
                "Estado"
            };
            for (int i = 0; i < expectedHeaders.length; i++) {
                Cell cell = header.getCell(i);
                assertThat(cell.getStringCellValue()).isEqualTo(expectedHeaders[i]);
                Font font = wb.getFontAt(cell.getCellStyle().getFontIndex());
                assertThat(font.getBold()).isTrue();
            }

            Row row1 = sheet.getRow(1);
            Cell dateCell = row1.getCell(0);
            assertThat(dateCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(dateCell)).isTrue();
            assertThat(dateCell.getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(dateCell.getCellStyle().getDataFormatString())
                    .isEqualTo(XlsxExportWriter.DATE_FORMAT);

            Cell amountCell = row1.getCell(8);
            assertThat(amountCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(amountCell.getNumericCellValue()).isEqualTo(15250.50);
            assertThat(amountCell.getCellStyle().getDataFormatString())
                    .isEqualTo(XlsxExportWriter.NUMBER_FORMAT);

            Cell lensAmountCell = row1.getCell(9);
            assertThat(lensAmountCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(lensAmountCell.getNumericCellValue()).isEqualTo(15250.50);
            assertThat(row1.getCell(10).getStringCellValue()).isEqualTo("2/6");
            assertThat(row1.getCell(11).getStringCellValue()).isEqualTo("Confirmado");

            Row row2 = sheet.getRow(2);
            Cell formulaLikeCell = row2.getCell(2);
            assertThat(formulaLikeCell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(formulaLikeCell.getStringCellValue()).isEqualTo("=cmd|'/C calc'!A0");
            assertThat(row2.getCell(3)).isNull();
            assertThat(row2.getCell(9)).isNull();
        }
    }

    @Test
    void writesMonthlySummaryWorkbookWithSheetResumenBoldHeaderAndTotalRow() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<MonthlySummaryRow> rows = List.of(
                new MonthlySummaryRow(
                        "Enero",
                        new BigDecimal("200000.0000"),
                        new BigDecimal("80000.0000"),
                        new BigDecimal("120000.0000"),
                        new BigDecimal("60.0000"),
                        false),
                new MonthlySummaryRow(
                        "Total",
                        new BigDecimal("200000.0000"),
                        new BigDecimal("80000.0000"),
                        new BigDecimal("120000.0000"),
                        new BigDecimal("60.0000"),
                        true));

        writer.writeMonthlySummary(out, rows);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            XSSFSheet sheet = wb.getSheet(XlsxExportWriter.SUMMARY_SHEET_NAME);
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Mes");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Ingresos");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Gastos");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Balance");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Tasa de ahorro %");

            Row january = sheet.getRow(1);
            assertThat(january.getCell(0).getStringCellValue()).isEqualTo("Enero");
            assertThat(january.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(january.getCell(1).getNumericCellValue()).isEqualTo(200000.0);
            assertThat(january.getCell(1).getCellStyle().getDataFormatString())
                    .isEqualTo(XlsxExportWriter.NUMBER_FORMAT);

            Row total = sheet.getRow(2);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(wb.getFontAt(total.getCell(0).getCellStyle().getFontIndex()).getBold())
                    .isTrue();
            assertThat(wb.getFontAt(total.getCell(3).getCellStyle().getFontIndex()).getBold())
                    .isTrue();
            assertThat(total.getCell(4).getNumericCellValue()).isEqualTo(60.0);
        }
    }
}
