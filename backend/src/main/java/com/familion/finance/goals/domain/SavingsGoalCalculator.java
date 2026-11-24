package com.familion.finance.goals.domain;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SavingsGoalCalculator {

    public static final String UVA_INDEX = "UVA";
    public static final String IPC_ACUMULADO_INDEX = "IPC_ACUMULADO";
    private static final String ARS_CURRENCY = "ARS";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public interface RateProvider {
        BigDecimal convert(
                BigDecimal amount,
                String fromCurrency,
                String toCurrency,
                LocalDate date,
                String rateSource);

        Optional<BigDecimal> valueAt(String indexCode, LocalDate date);

        Optional<BigDecimal> latestValue(String indexCode);
    }

    public record Progress(
            BigDecimal accumulatedInUnit,
            BigDecimal progressPct,
            BigDecimal remainingInUnit,
            Integer monthsLeft,
            BigDecimal suggestedMonthlyInUnit) {}

    public BigDecimal convertContributionToGoalUnit(
            BigDecimal amount,
            String contributionCurrency,
            TargetUnit targetUnit,
            LocalDate contributionDate,
            LocalDate goalCreatedDate,
            String baseCurrency,
            String rateSource,
            RateProvider rateProvider) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(contributionCurrency, "contributionCurrency must not be null");
        Objects.requireNonNull(targetUnit, "targetUnit must not be null");
        Objects.requireNonNull(contributionDate, "contributionDate must not be null");
        Objects.requireNonNull(goalCreatedDate, "goalCreatedDate must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(rateProvider, "rateProvider must not be null");

        if (!isPositive(amount)) {
            throw new BusinessRuleException("Contribution amount must be greater than zero");
        }

        String normalizedCurrency = contributionCurrency.trim().toUpperCase(Locale.ROOT);
        if (normalizedCurrency.isEmpty()) {
            throw new BusinessRuleException("Contribution currency is required");
        }

        BigDecimal converted = switch (targetUnit) {
            case ARS, USD, EUR -> Money.scale(rateProvider.convert(
                    amount, normalizedCurrency, targetUnit.name(), contributionDate, rateSource));
            case UVA -> convertToUva(
                    amount,
                    normalizedCurrency,
                    contributionDate,
                    baseCurrency,
                    rateSource,
                    rateProvider);
            case ARS_IPC -> convertToArsIpc(
                    amount,
                    normalizedCurrency,
                    contributionDate,
                    goalCreatedDate,
                    rateSource,
                    rateProvider);
        };

        if (!isPositive(converted)) {
            throw new BusinessRuleException(
                    "Converted contribution amount in goal unit must be greater than zero");
        }
        return converted;
    }

    public Progress computeProgress(
            BigDecimal targetAmount,
            BigDecimal rawAccumulated,
            LocalDate targetDate,
            LocalDate today) {
        Objects.requireNonNull(targetAmount, "targetAmount must not be null");
        Objects.requireNonNull(today, "today must not be null");
        if (!isPositive(targetAmount)) {
            throw new BusinessRuleException("Target amount must be greater than zero");
        }

        BigDecimal target = Money.scale(targetAmount);
        BigDecimal accumulated =
                rawAccumulated == null ? Money.scale(BigDecimal.ZERO) : Money.scale(rawAccumulated);
        if (accumulated.compareTo(BigDecimal.ZERO) < 0) {
            accumulated = Money.scale(BigDecimal.ZERO);
        }

        BigDecimal rawPct = Money.divide(accumulated.multiply(ONE_HUNDRED), target);
        BigDecimal maxPct = Money.scale(ONE_HUNDRED);
        BigDecimal progressPct = rawPct.compareTo(maxPct) > 0 ? maxPct : rawPct;

        BigDecimal diff = target.subtract(accumulated);
        BigDecimal remaining = diff.compareTo(BigDecimal.ZERO) > 0
                ? Money.scale(diff)
                : Money.scale(BigDecimal.ZERO);

        Integer monthsLeft = null;
        BigDecimal suggestedMonthly = null;
        if (targetDate != null) {
            long wholeMonths = ChronoUnit.MONTHS.between(today, targetDate);
            monthsLeft = Math.toIntExact(Math.max(1L, wholeMonths));
            suggestedMonthly = Money.divide(remaining, BigDecimal.valueOf(monthsLeft));
        }

        return new Progress(accumulated, progressPct, remaining, monthsLeft, suggestedMonthly);
    }

    public BigDecimal computeAccumulatedInBaseToday(
            BigDecimal accumulatedInUnit,
            TargetUnit targetUnit,
            LocalDate goalCreatedDate,
            LocalDate today,
            String baseCurrency,
            String rateSource,
            RateProvider rateProvider) {
        Objects.requireNonNull(targetUnit, "targetUnit must not be null");
        Objects.requireNonNull(goalCreatedDate, "goalCreatedDate must not be null");
        Objects.requireNonNull(today, "today must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(rateProvider, "rateProvider must not be null");

        BigDecimal acc = accumulatedInUnit == null
                ? Money.scale(BigDecimal.ZERO)
                : Money.scale(accumulatedInUnit);

        return switch (targetUnit) {
            case ARS, USD, EUR -> Money.scale(rateProvider.convert(
                    acc, targetUnit.name(), baseCurrency, today, rateSource));
            case UVA -> computeUvaInBaseToday(acc, today, baseCurrency, rateSource, rateProvider);
            case ARS_IPC -> computeArsIpcInBaseToday(
                    acc, goalCreatedDate, today, baseCurrency, rateSource, rateProvider);
        };
    }

    private BigDecimal convertToUva(
            BigDecimal amount,
            String currency,
            LocalDate contributionDate,
            String baseCurrency,
            String rateSource,
            RateProvider rateProvider) {
        if (UVA_INDEX.equals(currency)) {
            return Money.scale(amount);
        }
        BigDecimal inBase =
                rateProvider.convert(amount, currency, baseCurrency, contributionDate, rateSource);
        BigDecimal uvaValue = rateProvider
                .valueAt(UVA_INDEX, contributionDate)
                .or(() -> rateProvider.latestValue(UVA_INDEX))
                .orElse(null);
        if (!isPositive(uvaValue)) {
            throw new BusinessRuleException(
                    "UVA index value is not available for date " + contributionDate);
        }
        return Money.divide(inBase, uvaValue);
    }

    private BigDecimal convertToArsIpc(
            BigDecimal amount,
            String currency,
            LocalDate contributionDate,
            LocalDate goalCreatedDate,
            String rateSource,
            RateProvider rateProvider) {
        BigDecimal amountArs =
                rateProvider.convert(amount, currency, ARS_CURRENCY, contributionDate, rateSource);
        Optional<BigDecimal> ipcAtCreation =
                rateProvider.valueAt(IPC_ACUMULADO_INDEX, goalCreatedDate);
        Optional<BigDecimal> ipcAtContribution =
                rateProvider.valueAt(IPC_ACUMULADO_INDEX, contributionDate);
        if (ipcAtCreation.isPresent()
                && ipcAtContribution.isPresent()
                && isPositive(ipcAtCreation.get())
                && isPositive(ipcAtContribution.get())) {
            return Money.divide(
                    amountArs.multiply(ipcAtCreation.get()), ipcAtContribution.get());
        }
        return Money.scale(amountArs);
    }

    private BigDecimal computeUvaInBaseToday(
            BigDecimal accUva,
            LocalDate today,
            String baseCurrency,
            String rateSource,
            RateProvider rateProvider) {
        Optional<BigDecimal> uvaToday =
                rateProvider.valueAt(UVA_INDEX, today).or(() -> rateProvider.latestValue(UVA_INDEX));
        if (uvaToday.isEmpty() || !isPositive(uvaToday.get())) {
            return null;
        }
        BigDecimal inArs = Money.scale(accUva.multiply(uvaToday.get()));
        if (ARS_CURRENCY.equalsIgnoreCase(baseCurrency)) {
            return inArs;
        }
        return Money.scale(
                rateProvider.convert(inArs, ARS_CURRENCY, baseCurrency, today, rateSource));
    }

    private BigDecimal computeArsIpcInBaseToday(
            BigDecimal accArsAtCreation,
            LocalDate goalCreatedDate,
            LocalDate today,
            String baseCurrency,
            String rateSource,
            RateProvider rateProvider) {
        Optional<BigDecimal> ipcAtCreation =
                rateProvider.valueAt(IPC_ACUMULADO_INDEX, goalCreatedDate);
        Optional<BigDecimal> ipcToday = rateProvider
                .valueAt(IPC_ACUMULADO_INDEX, today)
                .or(() -> rateProvider.latestValue(IPC_ACUMULADO_INDEX));

        BigDecimal inArsToday;
        if (ipcAtCreation.isPresent()
                && ipcToday.isPresent()
                && isPositive(ipcAtCreation.get())
                && isPositive(ipcToday.get())) {
            inArsToday = Money.divide(
                    accArsAtCreation.multiply(ipcToday.get()), ipcAtCreation.get());
        } else {
            inArsToday = accArsAtCreation;
        }

        if (ARS_CURRENCY.equalsIgnoreCase(baseCurrency)) {
            return inArsToday;
        }
        return Money.scale(
                rateProvider.convert(inArsToday, ARS_CURRENCY, baseCurrency, today, rateSource));
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
