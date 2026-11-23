package com.familion.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money {

    public static final int SCALE = 4;
    public static final int RATE_SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private Money() {}

    public static BigDecimal scale(BigDecimal v) {
        Objects.requireNonNull(v, "value must not be null");
        return v.setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal rate(BigDecimal v) {
        Objects.requireNonNull(v, "rate must not be null");
        return v.setScale(RATE_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        Objects.requireNonNull(a, "dividend must not be null");
        Objects.requireNonNull(b, "divisor must not be null");
        return a.divide(b, SCALE, ROUNDING_MODE);
    }
}
