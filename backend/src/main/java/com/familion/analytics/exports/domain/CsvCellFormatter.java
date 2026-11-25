package com.familion.analytics.exports.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class CsvCellFormatter {

    public static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    public static final char SEPARATOR = ';';
    public static final String LINE_ENDING = "\r\n";

    private static final int DISPLAY_SCALE = 2;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CsvCellFormatter() {}

    public static String formatText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return escape(preventCsvInjection(raw));
    }

    public static String preventCsvInjection(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '='
                || first == '+'
                || first == '-'
                || first == '@'
                || first == '\t'
                || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf(SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN)
                .toPlainString()
                .replace('.', ',');
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return DATE_FORMATTER.format(date);
    }
}
