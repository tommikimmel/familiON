package com.familion.analytics.exports.infrastructure.format;

import com.familion.analytics.exports.application.ExportTransactionLine;
import com.familion.analytics.exports.application.ExportWriter;
import com.familion.analytics.exports.application.MonthlySummaryRow;
import com.familion.analytics.exports.domain.ExportFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class XlsxExportWriter implements ExportWriter {

    public static final String TRANSACTIONS_SHEET_NAME = "Movimientos";
    public static final String SUMMARY_SHEET_NAME = "Resumen";
    public static final String NUMBER_FORMAT = "#,##0.00";
    public static final String DATE_FORMAT = "dd/mm/yyyy";

    private static final int ROW_ACCESS_WINDOW = 100;
    private static final int DISPLAY_SCALE = 2;
    private static final int CHAR_WIDTH_UNIT = 256;

    private static final int[] TRANSACTION_COLUMN_WIDTHS = {
        13, // Fecha
        24, // Tipo
        34, // Descripción
        20, // Categoría
        22, // Cuenta
        22, // Pagado por
        22, // Atribuido a
        10, // Moneda
        16, // Monto
        20, // Monto ({unit})
        10, // Cuota
        14  // Estado
    };

    private static final int[] SUMMARY_COLUMN_WIDTHS = {
        16, // Mes
        18, // Ingresos
        18, // Gastos
        18, // Balance
        18  // Tasa de ahorro %
    };

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public void writeTransactions(OutputStream out, String lensUnit, TransactionRowSource rowSource)
            throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW)) {
            workbook.setCompressTempFiles(true);
            WorkbookStyles styles = WorkbookStyles.create(workbook);

            SXSSFSheet sheet = workbook.createSheet(TRANSACTIONS_SHEET_NAME);
            sheet.createFreezePane(0, 1);
            applyColumnWidths(sheet, TRANSACTION_COLUMN_WIDTHS);

            String[] headers = {
                "Fecha",
                "Tipo",
                "Descripción",
                "Categoría",
                "Cuenta",
                "Pagado por",
                "Atribuido a",
                "Moneda",
                "Monto",
                "Monto (" + lensUnit + ")",
                "Cuota",
                "Estado"
            };
            writeHeaderRow(sheet, headers, styles.headerStyle());

            AtomicInteger rowIndex = new AtomicInteger(1);
            rowSource.streamTo(line -> writeTransactionRow(sheet, rowIndex.getAndIncrement(), line, styles));

            workbook.write(out);
            out.flush();
        }
    }

    @Override
    public void writeMonthlySummary(OutputStream out, List<MonthlySummaryRow> rows)
            throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW)) {
            workbook.setCompressTempFiles(true);
            WorkbookStyles styles = WorkbookStyles.create(workbook);

            SXSSFSheet sheet = workbook.createSheet(SUMMARY_SHEET_NAME);
            sheet.createFreezePane(0, 1);
            applyColumnWidths(sheet, SUMMARY_COLUMN_WIDTHS);

            String[] headers = {"Mes", "Ingresos", "Gastos", "Balance", "Tasa de ahorro %"};
            writeHeaderRow(sheet, headers, styles.headerStyle());

            int rowIndex = 1;
            for (MonthlySummaryRow summaryRow : rows) {
                Row row = sheet.createRow(rowIndex++);
                CellStyle labelStyle = summaryRow.totalRow() ? styles.totalLabelStyle() : null;
                CellStyle numStyle =
                        summaryRow.totalRow() ? styles.totalNumberStyle() : styles.numberStyle();

                Cell labelCell = row.createCell(0);
                labelCell.setCellValue(summaryRow.label());
                if (labelStyle != null) {
                    labelCell.setCellStyle(labelStyle);
                }

                setNumericCell(row, 1, summaryRow.income(), numStyle);
                setNumericCell(row, 2, summaryRow.expense(), numStyle);
                setNumericCell(row, 3, summaryRow.balance(), numStyle);
                setNumericCell(row, 4, summaryRow.savingsRatePct(), numStyle);
            }

            workbook.write(out);
            out.flush();
        }
    }

    private static void writeHeaderRow(SXSSFSheet sheet, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private static void writeTransactionRow(
            SXSSFSheet sheet, int rowIndex, ExportTransactionLine line, WorkbookStyles styles) {
        Row row = sheet.createRow(rowIndex);

        if (line.date() != null) {
            Cell dateCell = row.createCell(0);
            dateCell.setCellValue(line.date());
            dateCell.setCellStyle(styles.dateStyle());
        }
        setTextCell(row, 1, line.typeLabel());
        setTextCell(row, 2, line.description());
        setTextCell(row, 3, line.categoryName());
        setTextCell(row, 4, line.accountName());
        setTextCell(row, 5, line.paidByName());
        setTextCell(row, 6, line.attributedToName());
        setTextCell(row, 7, line.currencyCode());
        setNumericCell(row, 8, line.amount(), styles.numberStyle());
        setNumericCell(row, 9, line.lensAmount(), styles.numberStyle());
        setTextCell(row, 10, line.installmentLabel());
        setTextCell(row, 11, line.statusLabel());
    }

    private static void setTextCell(Row row, int colIndex, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        row.createCell(colIndex).setCellValue(value);
    }

    private static void setNumericCell(Row row, int colIndex, BigDecimal value, CellStyle style) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value.setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN).doubleValue());
        cell.setCellStyle(style);
    }

    private static void applyColumnWidths(SXSSFSheet sheet, int[] widthsInChars) {
        for (int i = 0; i < widthsInChars.length; i++) {
            sheet.setColumnWidth(i, widthsInChars[i] * CHAR_WIDTH_UNIT);
        }
    }

    private record WorkbookStyles(
            CellStyle headerStyle,
            CellStyle dateStyle,
            CellStyle numberStyle,
            CellStyle totalLabelStyle,
            CellStyle totalNumberStyle) {

        static WorkbookStyles create(SXSSFWorkbook workbook) {
            DataFormat dataFormat = workbook.createDataFormat();
            short numberFormatIdx = dataFormat.getFormat(NUMBER_FORMAT);
            short dateFormatIdx = dataFormat.getFormat(DATE_FORMAT);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(dateFormatIdx);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(numberFormatIdx);

            CellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(boldFont);
            totalLabelStyle.setBorderTop(BorderStyle.THIN);

            CellStyle totalNumberStyle = workbook.createCellStyle();
            totalNumberStyle.setFont(boldFont);
            totalNumberStyle.setBorderTop(BorderStyle.THIN);
            totalNumberStyle.setDataFormat(numberFormatIdx);

            return new WorkbookStyles(
                    headerStyle, dateStyle, numberStyle, totalLabelStyle, totalNumberStyle);
        }
    }
}
