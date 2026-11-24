package com.familion.finance.exchanges.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.finance.exchanges.domain.PositionCalculator.ExchangeLeg;
import com.familion.finance.exchanges.domain.PositionCalculator.PositionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PositionCalculatorTest {

    private final PositionCalculator calculator = new PositionCalculator();

    @Test
    void buyThenPartialSellComputesWeightedAverageCostAndUnrealizedResult() {
        List<ExchangeLeg> legs = List.of(
                new ExchangeLeg("ARS", new BigDecimal("1185000.0000"), "USD", new BigDecimal("1000.0000")),
                new ExchangeLeg("USD", new BigDecimal("200.0000"), "ARS", new BigDecimal("250000.0000")));

        Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("1250.000000"));
        List<PositionSnapshot> positions =
                calculator.calculate("ARS", legs, code -> Optional.ofNullable(rates.get(code)));

        assertThat(positions).hasSize(1);
        PositionSnapshot usd = positions.getFirst();
        assertThat(usd.currencyCode()).isEqualTo("USD");
        assertThat(usd.units()).isEqualByComparingTo("800.0000");
        assertThat(usd.weightedAvgRate()).isEqualByComparingTo("1185.000000");
        assertThat(usd.totalCostBase()).isEqualByComparingTo("948000.0000");
        assertThat(usd.currentRate()).isEqualByComparingTo("1250.000000");
        assertThat(usd.currentValueBase()).isEqualByComparingTo("1000000.0000");
        assertThat(usd.unrealizedResultBase()).isEqualByComparingTo("52000.0000");
        assertThat(usd.unrealizedResultPct()).isEqualByComparingTo("5.4852");
    }

    @Test
    void multipleBuysComputeWeightedAverageRate() {
        List<ExchangeLeg> legs = List.of(
                new ExchangeLeg("ARS", new BigDecimal("100000.0000"), "USD", new BigDecimal("100.0000")),
                new ExchangeLeg("ARS", new BigDecimal("120000.0000"), "USD", new BigDecimal("100.0000")),
                new ExchangeLeg("USD", new BigDecimal("50.0000"), "ARS", new BigDecimal("65000.0000")));

        List<PositionSnapshot> positions = calculator.calculate("ARS", legs, code -> Optional.empty());

        assertThat(positions).hasSize(1);
        PositionSnapshot usd = positions.getFirst();
        assertThat(usd.units()).isEqualByComparingTo("150.0000");
        assertThat(usd.weightedAvgRate()).isEqualByComparingTo("1100.000000");
        assertThat(usd.totalCostBase()).isEqualByComparingTo("165000.0000");
        assertThat(usd.currentRate()).isNull();
        assertThat(usd.currentValueBase()).isNull();
        assertThat(usd.unrealizedResultBase()).isNull();
        assertThat(usd.unrealizedResultPct()).isNull();
    }

    @Test
    void fullOrOverSellResetsPositionToZeroAndNullWeightedAvgRate() {
        List<ExchangeLeg> legs = List.of(
                new ExchangeLeg("ARS", new BigDecimal("100000.0000"), "USD", new BigDecimal("100.0000")),
                new ExchangeLeg("USD", new BigDecimal("150.0000"), "ARS", new BigDecimal("180000.0000")));

        Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("1200.000000"));
        List<PositionSnapshot> positions =
                calculator.calculate("ARS", legs, code -> Optional.ofNullable(rates.get(code)));

        assertThat(positions).hasSize(1);
        PositionSnapshot usd = positions.getFirst();
        assertThat(usd.units()).isEqualByComparingTo("0.0000");
        assertThat(usd.weightedAvgRate()).isNull();
        assertThat(usd.totalCostBase()).isEqualByComparingTo("0.0000");
        assertThat(usd.currentRate()).isEqualByComparingTo("1200.000000");
        assertThat(usd.currentValueBase()).isEqualByComparingTo("0.0000");
        assertThat(usd.unrealizedResultBase()).isEqualByComparingTo("0.0000");
        assertThat(usd.unrealizedResultPct()).isNull();
    }

    @Test
    void sellWithoutPriorBuyDoesNotDivideByZero() {
        List<ExchangeLeg> legs = List.of(
                new ExchangeLeg("USD", new BigDecimal("100.0000"), "ARS", new BigDecimal("120000.0000")));

        List<PositionSnapshot> positions = calculator.calculate("ARS", legs, code -> Optional.empty());

        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().units()).isEqualByComparingTo("0.0000");
        assertThat(positions.getFirst().weightedAvgRate()).isNull();
        assertThat(positions.getFirst().totalCostBase()).isEqualByComparingTo("0.0000");
    }

    @Test
    void swapTransfersSourceCostBasisAtPppToTargetCurrency() {
        List<ExchangeLeg> legs = List.of(
                new ExchangeLeg("ARS", new BigDecimal("1200000.0000"), "USD", new BigDecimal("1000.0000")),
                new ExchangeLeg("USD", new BigDecimal("400.0000"), "EUR", new BigDecimal("350.0000")));

        Map<String, BigDecimal> rates = Map.of(
                "USD", new BigDecimal("1250.000000"),
                "EUR", new BigDecimal("1400.000000"));
        List<PositionSnapshot> positions =
                calculator.calculate("ARS", legs, code -> Optional.ofNullable(rates.get(code)));

        assertThat(positions).hasSize(2);
        PositionSnapshot eur = positions.get(0);
        PositionSnapshot usd = positions.get(1);

        assertThat(eur.currencyCode()).isEqualTo("EUR");
        assertThat(eur.units()).isEqualByComparingTo("350.0000");
        assertThat(eur.totalCostBase()).isEqualByComparingTo("480000.0000");
        assertThat(eur.weightedAvgRate()).isEqualByComparingTo("1371.428571");
        assertThat(eur.currentValueBase()).isEqualByComparingTo("490000.0000");
        assertThat(eur.unrealizedResultBase()).isEqualByComparingTo("10000.0000");

        assertThat(usd.currencyCode()).isEqualTo("USD");
        assertThat(usd.units()).isEqualByComparingTo("600.0000");
        assertThat(usd.totalCostBase()).isEqualByComparingTo("720000.0000");
        assertThat(usd.weightedAvgRate()).isEqualByComparingTo("1200.000000");
    }

    @Test
    void resolvesRateSourceAccordingToCurrencyAndFamilyReferenceRateSource() {
        assertThat(PositionCalculator.resolveRateSource("USD", "USD_BLUE")).contains("USD_BLUE");
        assertThat(PositionCalculator.resolveRateSource("USD", "USD_OFICIAL"))
                .contains("USD_OFICIAL");
        assertThat(PositionCalculator.resolveRateSource("USD", "EUR_OFICIAL")).contains("USD_MEP");
        assertThat(PositionCalculator.resolveRateSource("USD", null)).contains("USD_MEP");
        assertThat(PositionCalculator.resolveRateSource("EUR", "USD_BLUE")).contains("EUR_OFICIAL");
        assertThat(PositionCalculator.resolveRateSource("BRL", "USD_MEP")).isEmpty();
    }
}
