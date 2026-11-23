package com.familion.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void scalesMoneyToFourDecimalsUsingHalfEven() {
        assertThat(Money.SCALE).isEqualTo(4);
        assertThat(Money.scale(new BigDecimal("10.12345"))).isEqualByComparingTo("10.1234");
        assertThat(Money.scale(new BigDecimal("10.12355"))).isEqualByComparingTo("10.1236");
        assertThat(Money.scale(new BigDecimal("42"))).isEqualTo(new BigDecimal("42.0000"));
    }

    @Test
    void scalesRateToSixDecimalsUsingHalfEven() {
        assertThat(Money.RATE_SCALE).isEqualTo(6);
        assertThat(Money.rate(new BigDecimal("1200.1234565"))).isEqualByComparingTo("1200.123456");
        assertThat(Money.rate(new BigDecimal("1200.1234575"))).isEqualByComparingTo("1200.123458");
        assertThat(Money.rate(new BigDecimal("1.5"))).isEqualTo(new BigDecimal("1.500000"));
    }

    @Test
    void dividesToFourDecimalsUsingHalfEven() {
        assertThat(Money.divide(new BigDecimal("10"), new BigDecimal("3")))
                .isEqualTo(new BigDecimal("3.3333"));
        assertThat(Money.divide(new BigDecimal("1"), new BigDecimal("80000")))
                .isEqualTo(new BigDecimal("0.0000"));
        assertThat(Money.divide(new BigDecimal("3"), new BigDecimal("20000")))
                .isEqualTo(new BigDecimal("0.0002"));
    }
}
