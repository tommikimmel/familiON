package com.familion.finance.installments.application;

import com.familion.common.money.Money;
import com.familion.finance.installments.domain.InstallmentMetricsCalculator;
import com.familion.finance.installments.domain.InstallmentScheduleCalculator;
import com.familion.finance.transactions.application.api.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class InstallmentPlanViewAssembler {

    private final InstallmentScheduleCalculator scheduleCalculator;
    private final InstallmentMetricsCalculator metricsCalculator;

    InstallmentPlanViewAssembler(
            InstallmentScheduleCalculator scheduleCalculator,
            InstallmentMetricsCalculator metricsCalculator) {
        this.scheduleCalculator = scheduleCalculator;
        this.metricsCalculator = metricsCalculator;
    }

    InstallmentPlanView toView(
            InstallmentPlanReadRow row,
            List<InstallmentTransactionRow> installments,
            LocalDate today,
            BigDecimal latestMonthlyIpcPct) {
        List<InstallmentTransactionRow> sorted = installments == null
                ? List.of()
                : installments.stream()
                        .sorted(Comparator.comparingInt(InstallmentTransactionRow::installmentNumber)
                                .thenComparing(InstallmentTransactionRow::transactionDate))
                        .toList();

        int paidInstallments = 0;
        BigDecimal remainingAmount = BigDecimal.ZERO;
        List<InstallmentMetricsCalculator.ProjectedInstallment> projectedForReal = new ArrayList<>();
        InstallmentPlanView.NextInstallmentSummary nextInstallment = null;

        for (InstallmentTransactionRow tx : sorted) {
            if (tx.status() == TransactionStatus.POSTED) {
                paidInstallments++;
            } else if (tx.status() == TransactionStatus.PROJECTED) {
                remainingAmount = remainingAmount.add(tx.amount());
                projectedForReal.add(new InstallmentMetricsCalculator.ProjectedInstallment(
                        tx.transactionDate(), tx.amount()));
                if (nextInstallment == null) {
                    nextInstallment = new InstallmentPlanView.NextInstallmentSummary(
                            tx.installmentNumber(), tx.transactionDate(), tx.amount());
                }
            }
        }

        InstallmentMetricsCalculator.SurchargeResult surcharge = metricsCalculator.calculateSurcharge(
                row.hasInterest(), row.cashPrice(), row.financedTotal());

        BigDecimal realRemainingAmount = metricsCalculator.calculateRealRemainingAmount(
                projectedForReal, today, latestMonthlyIpcPct);

        LocalDate lastBillingDate = scheduleCalculator.computeLastBillingDate(
                row.firstBillingDate(),
                row.totalInstallments(),
                row.creditCardClosingDay(),
                row.creditCardDueDay());

        InstallmentPlanView.MemberSummary attributedTo = sorted.isEmpty()
                ? new InstallmentPlanView.MemberSummary(row.userId(), row.creatorFullName())
                : new InstallmentPlanView.MemberSummary(
                        sorted.getFirst().attributedToUserId(),
                        sorted.getFirst().attributedToFullName());

        return new InstallmentPlanView(
                row.id(),
                row.description(),
                new InstallmentPlanView.CategorySummary(
                        row.categoryId(),
                        row.categoryName(),
                        row.categoryIcon(),
                        row.categoryColorHex()),
                new InstallmentPlanView.AccountSummary(
                        row.creditCardAccountId(), row.creditCardAccountName()),
                row.currencyCode(),
                row.totalInstallments(),
                paidInstallments,
                row.hasInterest(),
                row.cashPrice(),
                row.financedTotal(),
                row.installmentAmount(),
                surcharge.surchargeAmount(),
                surcharge.surchargePct(),
                row.purchaseDate(),
                row.firstBillingDate(),
                lastBillingDate,
                Money.scale(remainingAmount),
                realRemainingAmount,
                nextInstallment,
                row.status(),
                attributedTo);
    }

    InstallmentPlanDetailView toDetailView(
            InstallmentPlanReadRow row,
            List<InstallmentTransactionRow> installments,
            LocalDate today,
            BigDecimal latestMonthlyIpcPct) {
        InstallmentPlanView summary = toView(row, installments, today, latestMonthlyIpcPct);
        List<InstallmentPlanDetailView.InstallmentItemView> items = installments == null
                ? List.of()
                : installments.stream()
                        .sorted(Comparator.comparingInt(InstallmentTransactionRow::installmentNumber)
                                .thenComparing(InstallmentTransactionRow::transactionDate))
                        .map(tx -> new InstallmentPlanDetailView.InstallmentItemView(
                                tx.installmentNumber(),
                                tx.transactionDate(),
                                tx.amount(),
                                tx.status(),
                                tx.transactionId()))
                        .toList();
        return new InstallmentPlanDetailView(summary, items);
    }
}
