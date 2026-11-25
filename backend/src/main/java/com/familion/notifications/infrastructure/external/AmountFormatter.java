package com.familion.notifications.infrastructure.external;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class AmountFormatter {

    private static final DecimalFormatSymbols SYMBOLS = new DecimalFormatSymbols(Locale.of("es", "AR"));

    private AmountFormatter() {}

    public static String format(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return "$0";
        }
        DecimalFormat df;
        if (amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            df = new DecimalFormat("#,##0", SYMBOLS);
        } else {
            df = new DecimalFormat("#,##0.00", SYMBOLS);
        }
        String formatted = df.format(amount);
        if ("USD".equalsIgnoreCase(currencyCode)) {
            return "US$ " + formatted;
        }
        return "$" + formatted;
    }
}
