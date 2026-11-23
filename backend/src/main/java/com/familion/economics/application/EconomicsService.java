package com.familion.economics.application;

import com.familion.common.money.Money;
import com.familion.economics.application.api.EconomicSnapshotData;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.economics.application.api.FamilyEconomicContext;
import com.familion.economics.domain.EconomicIndex;
import com.familion.economics.domain.EconomicsCalculator;
import com.familion.economics.domain.EconomicsCalculator.IndexLookup;
import com.familion.economics.domain.IndexCode;
import com.familion.economics.infrastructure.persistence.EconomicIndexRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EconomicsService implements EconomicsApi {

    private final EconomicIndexRepository repository;
    private final EconomicsCalculator calculator;

    public EconomicsService(EconomicIndexRepository repository) {
        this.repository = repository;
        this.calculator = new EconomicsCalculator();
    }

    @Override
    public EconomicSnapshotData snapshotFor(
            FamilyEconomicContext ctx, LocalDate date, BigDecimal amount, String currencyCode) {
        return calculator.snapshotFor(ctx, date, amount, currencyCode, indexLookup());
    }

    @Override
    public BigDecimal convert(
            BigDecimal amount,
            String fromCurrency,
            String toCurrency,
            LocalDate date,
            String rateSource) {
        return calculator.convert(amount, fromCurrency, toCurrency, date, rateSource, indexLookup());
    }

    @Override
    public Optional<BigDecimal> valueAt(String indexCode, LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return IndexCode.fromCode(indexCode).flatMap(code -> valueAt(code, date));
    }

    @Override
    public Optional<BigDecimal> latestValue(String indexCode) {
        return IndexCode.fromCode(indexCode).flatMap(this::latestValue);
    }

    private Optional<BigDecimal> valueAt(IndexCode indexCode, LocalDate date) {
        return repository
                .findTopByIndexCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        indexCode, date)
                .map(EconomicIndex::getValue)
                .map(Money::rate);
    }

    private Optional<BigDecimal> latestValue(IndexCode indexCode) {
        return repository
                .findTopByIndexCodeOrderByEffectiveDateDesc(indexCode)
                .map(EconomicIndex::getValue)
                .map(Money::rate);
    }

    private IndexLookup indexLookup() {
        return new IndexLookup() {
            @Override
            public Optional<BigDecimal> valueAt(IndexCode indexCode, LocalDate date) {
                return EconomicsService.this.valueAt(indexCode, date);
            }

            @Override
            public Optional<BigDecimal> latestValue(IndexCode indexCode) {
                return EconomicsService.this.latestValue(indexCode);
            }
        };
    }
}
