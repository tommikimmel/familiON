package com.familion.family.domain;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;

public final class FamilyCatalog {

    public static final Set<String> VALID_CURRENCIES = Set.of("ARS", "USD", "EUR", "USDT");
    public static final Set<String> VALID_REFERENCE_CURRENCIES = Set.of("USD", "EUR", "UVA", "ARS");
    public static final Set<String> VALID_RATE_SOURCES =
            Set.of("USD_OFICIAL", "USD_MEP", "USD_BLUE", "EUR_OFICIAL", "EUR_BLUE", "UVA");

    public static final int MAX_FAMILY_NAME_LENGTH = 120;
    public static final int MAX_TIMEZONE_LENGTH = 60;

    private FamilyCatalog() {}

    public static boolean isValidCurrency(String currencyCode) {
        return currencyCode != null && VALID_CURRENCIES.contains(currencyCode);
    }

    public static boolean isValidReferenceCurrency(String referenceCurrency) {
        return referenceCurrency != null && VALID_REFERENCE_CURRENCIES.contains(referenceCurrency);
    }

    public static boolean isValidRateSource(String rateSource) {
        return rateSource != null && VALID_RATE_SOURCES.contains(rateSource);
    }

    public static boolean isValidTimezone(String timezone) {
        if (timezone == null || timezone.isBlank() || timezone.length() > MAX_TIMEZONE_LENGTH) {
            return false;
        }
        try {
            ZoneId.of(timezone);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }
}
