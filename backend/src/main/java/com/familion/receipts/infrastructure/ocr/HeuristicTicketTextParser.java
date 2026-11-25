package com.familion.receipts.infrastructure.ocr;

import com.familion.common.money.Money;
import com.familion.receipts.application.port.ReceiptDraft;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeuristicTicketTextParser {

    private static final int TOTAL_DETECTABLE_FIELDS = 4;
    private static final int MAX_MERCHANT_LENGTH = 120;

    private static final Pattern HAS_LETTERS = Pattern.compile("[\\p{L}]{2,}");
    private static final Pattern HEADER_NOISE = Pattern.compile(
            "^(TICKET|TIQUE|FACTURA)(\\b|\\s|/|-).*|^(TICKET|TIQUE|FACTURA)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TOTAL_LINE =
            Pattern.compile("(?i)(?<!SUB[-\\s]?)\\bTOTAL\\b");
    private static final Pattern AMOUNT_TOKEN =
            Pattern.compile("(?<!\\d)(\\d{1,3}(?:[.,]\\d{3})+[.,]\\d{1,2}|\\d+[.,]\\d{1,2}|\\d{1,3}(?:\\.\\d{3})+|\\d+)(?!\\d)");
    private static final Pattern PURE_AMOUNT_LINE =
            Pattern.compile("^\\s*(?:U\\$S|US\\$|USD|ARS|\\$)?\\s*(\\d[\\d.,]*)\\s*$");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})(?!\\d)");

    private static final Pattern USD_PATTERN =
            Pattern.compile("(?i)(?:U\\$S|US\\$|\\bUSD\\b)");

    private static final Pattern FUEL_KEYWORDS =
            Pattern.compile("\\b(?:YPF|SHELL|AXION|PUMA)\\b");
    private static final Pattern SUPERMARKET_STRONG_KEYWORDS =
            Pattern.compile("\\b(?:COTO|CARREFOUR|JUMBO|DISCO|CHANGOMAS)\\b");
    private static final Pattern SUPERMARKET_DIA_KEYWORD =
            Pattern.compile("(?<!\\bBUEN\\s)(?<!\\bCADA\\s)(?<!\\bDEL\\s)\\bDIA\\b");
    private static final Pattern HEALTH_KEYWORDS =
            Pattern.compile("\\bFARMACIA\\w*\\b");

    public ReceiptDraft parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ReceiptDraft(
                    null, null, null, "ARS", "OTHER_EXPENSE", scaledConfidence(0), rawText);
        }

        List<String> lines = rawText.lines().map(String::trim).toList();
        String merchant = extractMerchant(lines);
        LocalDate date = extractDate(rawText);
        BigDecimal totalAmount = extractTotalAmount(lines);
        String currencyCode = extractCurrency(rawText);
        CategoryMatch categoryMatch = suggestCategory(rawText);

        int detected = 0;
        if (merchant != null) {
            detected++;
        }
        if (date != null) {
            detected++;
        }
        if (totalAmount != null) {
            detected++;
        }
        if (categoryMatch.matchedKeyword()) {
            detected++;
        }

        return new ReceiptDraft(
                merchant,
                date,
                totalAmount,
                currencyCode,
                categoryMatch.categoryKey(),
                scaledConfidence(detected),
                rawText);
    }

    private static String extractMerchant(List<String> lines) {
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            if (!HAS_LETTERS.matcher(line).find()) {
                continue;
            }
            String normalized = normalizeAsciiUpper(line);
            if (HEADER_NOISE.matcher(normalized).matches()) {
                continue;
            }
            String collapsed = line.replaceAll("\\s+", " ").trim();
            return collapsed.length() > MAX_MERCHANT_LENGTH
                    ? collapsed.substring(0, MAX_MERCHANT_LENGTH)
                    : collapsed;
        }
        return null;
    }

    private static LocalDate extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            String yearToken = matcher.group(3);
            int year = Integer.parseInt(yearToken);
            if (yearToken.length() == 2) {
                year += 2000;
            } else if (yearToken.length() != 4) {
                continue;
            }
            if (year < 2000 || year > 2100) {
                continue;
            }
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException ignored) {
                // Continue scanning for a valid calendar date.
            }
        }
        return null;
    }

    private static BigDecimal extractTotalAmount(List<String> lines) {
        BigDecimal lastTotal = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!TOTAL_LINE.matcher(line).find()) {
                continue;
            }
            BigDecimal candidate = extractLastAmountOnLine(line);
            if (candidate == null && i + 1 < lines.size()) {
                Matcher nextLineMatcher = PURE_AMOUNT_LINE.matcher(lines.get(i + 1));
                if (nextLineMatcher.matches()) {
                    candidate = parseLocalizedNumber(nextLineMatcher.group(1));
                }
            }
            if (candidate != null && candidate.signum() > 0) {
                lastTotal = Money.scale(candidate);
            }
        }
        return lastTotal;
    }

    private static BigDecimal extractLastAmountOnLine(String line) {
        Matcher matcher = AMOUNT_TOKEN.matcher(line);
        String lastToken = null;
        while (matcher.find()) {
            lastToken = matcher.group(1);
        }
        return lastToken != null ? parseLocalizedNumber(lastToken) : null;
    }

    static BigDecimal parseLocalizedNumber(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String token = rawToken.trim();
        int lastDot = token.lastIndexOf('.');
        int lastComma = token.lastIndexOf(',');

        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            if (lastComma > lastDot) {
                // Format like 1.234,56
                normalized = token.replace(".", "").replace(',', '.');
            } else {
                // Format like 1,234.56
                normalized = token.replace(",", "");
            }
        } else if (lastComma >= 0) {
            int decimals = token.length() - lastComma - 1;
            int commaCount = countOccurrences(token, ',');
            if (commaCount == 1 && (decimals == 1 || decimals == 2)) {
                normalized = token.replace(',', '.');
            } else {
                normalized = token.replace(",", "");
            }
        } else if (lastDot >= 0) {
            int decimals = token.length() - lastDot - 1;
            int dotCount = countOccurrences(token, '.');
            if (dotCount == 1 && (decimals == 1 || decimals == 2)) {
                normalized = token;
            } else {
                normalized = token.replace(".", "");
            }
        } else {
            normalized = token;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int countOccurrences(String value, char ch) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    private static String extractCurrency(String text) {
        return USD_PATTERN.matcher(text).find() ? "USD" : "ARS";
    }

    private static CategoryMatch suggestCategory(String rawText) {
        String normalized = normalizeAsciiUpper(rawText);
        if (FUEL_KEYWORDS.matcher(normalized).find()) {
            return new CategoryMatch("VEHICLES_FUEL", true);
        }
        if (SUPERMARKET_STRONG_KEYWORDS.matcher(normalized).find()) {
            return new CategoryMatch("SUPERMARKET", true);
        }
        if (HEALTH_KEYWORDS.matcher(normalized).find()) {
            return new CategoryMatch("HEALTH", true);
        }
        if (SUPERMARKET_DIA_KEYWORD.matcher(normalized).find()) {
            return new CategoryMatch("SUPERMARKET", true);
        }
        return new CategoryMatch("OTHER_EXPENSE", false);
    }

    private static String normalizeAsciiUpper(String input) {
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    private static BigDecimal scaledConfidence(int detectedFields) {
        return BigDecimal.valueOf(detectedFields)
                .divide(BigDecimal.valueOf(TOTAL_DETECTABLE_FIELDS), 2, RoundingMode.HALF_EVEN);
    }

    private record CategoryMatch(String categoryKey, boolean matchedKeyword) {}
}
