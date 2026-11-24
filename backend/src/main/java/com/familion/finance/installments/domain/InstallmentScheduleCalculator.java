package com.familion.finance.installments.domain;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InstallmentScheduleCalculator {

    public static final int MIN_INSTALLMENTS = 2;
    public static final int MAX_INSTALLMENTS = 60;
    public static final int DEFAULT_DUE_DAY = 10;

    public record ScheduledInstallment(int number, LocalDate date, BigDecimal amount) {}

    public record PlanSchedule(
            BigDecimal financedTotal,
            BigDecimal installmentAmount,
            LocalDate firstBillingDate,
            LocalDate lastBillingDate,
            List<ScheduledInstallment> installments) {}

    public PlanSchedule calculate(
            LocalDate purchaseDate,
            int totalInstallments,
            AmountMode amountMode,
            BigDecimal amount,
            Integer closingDay,
            Integer dueDay) {
        Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
        Objects.requireNonNull(amountMode, "amountMode must not be null");

        if (totalInstallments < MIN_INSTALLMENTS || totalInstallments > MAX_INSTALLMENTS) {
            throw new BusinessRuleException(
                    "Total installments must be between "
                            + MIN_INSTALLMENTS
                            + " and "
                            + MAX_INSTALLMENTS);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Installment plan amount must be greater than zero");
        }

        BigDecimal financedTotal;
        BigDecimal baseInstallmentAmount;
        BigDecimal lastInstallmentAmount;

        if (amountMode == AmountMode.TOTAL) {
            financedTotal = Money.scale(amount);
            if (financedTotal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException(
                        "Installment plan amount must be greater than zero");
            }
            baseInstallmentAmount =
                    Money.divide(financedTotal, BigDecimal.valueOf(totalInstallments));
            BigDecimal sumFirstInstallments = baseInstallmentAmount.multiply(
                    BigDecimal.valueOf(totalInstallments - 1L));
            lastInstallmentAmount = Money.scale(financedTotal.subtract(sumFirstInstallments));
            if (baseInstallmentAmount.compareTo(BigDecimal.ZERO) <= 0
                    || lastInstallmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException(
                        "Each installment amount must be greater than zero");
            }
        } else {
            baseInstallmentAmount = Money.scale(amount);
            if (baseInstallmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException(
                        "Installment amount must be greater than zero");
            }
            lastInstallmentAmount = baseInstallmentAmount;
            financedTotal = Money.scale(
                    baseInstallmentAmount.multiply(BigDecimal.valueOf(totalInstallments)));
        }

        LocalDate firstBillingDate = resolveFirstBillingDate(purchaseDate, closingDay, dueDay);
        int targetDueDay = (closingDay != null && dueDay != null) ? dueDay : DEFAULT_DUE_DAY;

        List<ScheduledInstallment> installments = new ArrayList<>(totalInstallments);
        for (int number = 1; number <= totalInstallments; number++) {
            LocalDate billingDate = billingDateForInstallment(firstBillingDate, number, targetDueDay);
            BigDecimal installmentAmount =
                    number == totalInstallments ? lastInstallmentAmount : baseInstallmentAmount;
            installments.add(new ScheduledInstallment(number, billingDate, installmentAmount));
        }

        LocalDate lastBillingDate = installments.getLast().date();
        return new PlanSchedule(
                financedTotal,
                baseInstallmentAmount,
                firstBillingDate,
                lastBillingDate,
                List.copyOf(installments));
    }

    public LocalDate resolveFirstBillingDate(
            LocalDate purchaseDate, Integer closingDay, Integer dueDay) {
        Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
        YearMonth purchaseMonth = YearMonth.from(purchaseDate);

        if (closingDay == null || dueDay == null) {
            return clampDay(purchaseMonth.plusMonths(1), DEFAULT_DUE_DAY);
        }

        LocalDate closingDate = clampDay(purchaseMonth, closingDay);
        if (purchaseDate.isAfter(closingDate)) {
            closingDate = clampDay(purchaseMonth.plusMonths(1), closingDay);
        }
        YearMonth billingMonth = YearMonth.from(closingDate).plusMonths(1);
        return clampDay(billingMonth, dueDay);
    }

    public LocalDate computeLastBillingDate(
            LocalDate firstBillingDate,
            int totalInstallments,
            Integer closingDay,
            Integer dueDay) {
        Objects.requireNonNull(firstBillingDate, "firstBillingDate must not be null");
        int targetDueDay =
                (closingDay != null && dueDay != null) ? dueDay : firstBillingDate.getDayOfMonth();
        return billingDateForInstallment(firstBillingDate, totalInstallments, targetDueDay);
    }

    public LocalDate billingDateForInstallment(
            LocalDate firstBillingDate, int installmentNumber, int targetDueDay) {
        YearMonth targetMonth = YearMonth.from(firstBillingDate).plusMonths(installmentNumber - 1L);
        return clampDay(targetMonth, targetDueDay);
    }

    private static LocalDate clampDay(YearMonth yearMonth, int dayOfMonth) {
        int clampedDay = Math.min(Math.max(dayOfMonth, 1), yearMonth.lengthOfMonth());
        return yearMonth.atDay(clampedDay);
    }
}
