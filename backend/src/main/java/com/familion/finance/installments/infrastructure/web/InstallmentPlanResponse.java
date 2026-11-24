package com.familion.finance.installments.infrastructure.web;

import com.familion.finance.installments.application.InstallmentPlanView;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentPlanResponse(
        UUID id,
        String description,
        CategorySummary category,
        AccountSummary creditCardAccount,
        String currencyCode,
        int totalInstallments,
        int paidInstallments,
        boolean hasInterest,
        BigDecimal cashPrice,
        BigDecimal financedTotal,
        BigDecimal installmentAmount,
        BigDecimal surchargeAmount,
        BigDecimal surchargePct,
        LocalDate purchaseDate,
        LocalDate firstBillingDate,
        LocalDate lastBillingDate,
        BigDecimal remainingAmount,
        BigDecimal realRemainingAmount,
        NextInstallmentSummary nextInstallment,
        InstallmentPlanStatus status,
        MemberSummary attributedTo) {

    public record CategorySummary(UUID id, String name, String icon, String colorHex) {}

    public record AccountSummary(UUID id, String name) {}

    public record NextInstallmentSummary(int number, LocalDate date, BigDecimal amount) {}

    public record MemberSummary(UUID id, String fullName) {}

    public static InstallmentPlanResponse from(InstallmentPlanView view) {
        return new InstallmentPlanResponse(
                view.id(),
                view.description(),
                new CategorySummary(
                        view.category().id(),
                        view.category().name(),
                        view.category().icon(),
                        view.category().colorHex()),
                new AccountSummary(
                        view.creditCardAccount().id(), view.creditCardAccount().name()),
                view.currencyCode(),
                view.totalInstallments(),
                view.paidInstallments(),
                view.hasInterest(),
                view.cashPrice(),
                view.financedTotal(),
                view.installmentAmount(),
                view.surchargeAmount(),
                view.surchargePct(),
                view.purchaseDate(),
                view.firstBillingDate(),
                view.lastBillingDate(),
                view.remainingAmount(),
                view.realRemainingAmount(),
                view.nextInstallment() == null
                        ? null
                        : new NextInstallmentSummary(
                                view.nextInstallment().number(),
                                view.nextInstallment().date(),
                                view.nextInstallment().amount()),
                view.status(),
                new MemberSummary(view.attributedTo().id(), view.attributedTo().fullName()));
    }
}
