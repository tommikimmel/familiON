package com.familion.finance.installments.domain;

import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public final class InstallmentMetricsCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public record SurchargeResult(BigDecimal surchargeAmount, BigDecimal surchargePct) {}

    public record ProjectedInstallment(LocalDate date, BigDecimal amount) {}

    public SurchargeResult calculateSurcharge(
            boolean hasInterest, BigDecimal cashPrice, BigDecimal financedTotal) {
        Objects.requireNonNull(financedTotal, "financedTotal must not be null");
        if (!hasInterest || cashPrice == null || cashPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new SurchargeResult(null, null);
        }

        BigDecimal scaledCashPrice = Money.scale(cashPrice);
        BigDecimal scaledFinancedTotal = Money.scale(financedTotal);
        BigDecimal surchargeAmount = Money.scale(scaledFinancedTotal.subtract(scaledCashPrice));
        BigDecimal surchargePct =
                Money.divide(surchargeAmount.multiply(ONE_HUNDRED), scaledCashPrice);
        return new SurchargeResult(surchargeAmount, surchargePct);
    }

    public BigDecimal calculateRealRemainingAmount(
            List<ProjectedInstallment> projectedInstallments,
            LocalDate today,
            BigDecimal latestMonthlyIpcPct) {
        Objects.requireNonNull(today, "today must not be null");
        if (latestMonthlyIpcPct == null) {
            return null;
        }

        BigDecimal monthlyRate = latestMonthlyIpcPct.movePointLeft(2);
        BigDecimal baseFactor = BigDecimal.ONE.add(monthlyRate);
        if (baseFactor.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (projectedInstallments == null || projectedInstallments.isEmpty()) {
            return Money.scale(BigDecimal.ZERO);
        }

        YearMonth todayMonth = YearMonth.from(today);
        BigDecimal discountedTotal = BigDecimal.ZERO;

        for (ProjectedInstallment installment : projectedInstallments) {
            long monthsFromToday =
                    ChronoUnit.MONTHS.between(todayMonth, YearMonth.from(installment.date()));
            int k = (int) Math.max(0L, monthsFromToday);

            BigDecimal presentValue;
            if (k == 0) {
                presentValue = installment.amount();
            } else {
                BigDecimal discountFactor = baseFactor.pow(k, MathContext.DECIMAL128);
                presentValue = installment.amount().divide(discountFactor, MathContext.DECIMAL128);
            }
            discountedTotal = discountedTotal.add(presentValue, MathContext.DECIMAL128);
        }

        return Money.scale(discountedTotal);
    }
}
