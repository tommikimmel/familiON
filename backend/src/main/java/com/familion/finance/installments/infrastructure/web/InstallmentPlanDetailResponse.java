package com.familion.finance.installments.infrastructure.web;

import com.familion.finance.installments.application.InstallmentPlanDetailView;
import com.familion.finance.installments.application.InstallmentPlanView;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import com.familion.finance.transactions.application.api.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InstallmentPlanDetailResponse(
        UUID id,
        String description,
        InstallmentPlanResponse.CategorySummary category,
        InstallmentPlanResponse.AccountSummary creditCardAccount,
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
        InstallmentPlanResponse.NextInstallmentSummary nextInstallment,
        InstallmentPlanStatus status,
        InstallmentPlanResponse.MemberSummary attributedTo,
        List<InstallmentItemResponse> installments) {

    public record InstallmentItemResponse(
            int number,
            LocalDate date,
            BigDecimal amount,
            TransactionStatus status,
            UUID transactionId) {}

    public static InstallmentPlanDetailResponse from(InstallmentPlanDetailView detail) {
        InstallmentPlanView view = detail.plan();
        return new InstallmentPlanDetailResponse(
                view.id(),
                view.description(),
                new InstallmentPlanResponse.CategorySummary(
                        view.category().id(),
                        view.category().name(),
                        view.category().icon(),
                        view.category().colorHex()),
                new InstallmentPlanResponse.AccountSummary(
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
                        : new InstallmentPlanResponse.NextInstallmentSummary(
                                view.nextInstallment().number(),
                                view.nextInstallment().date(),
                                view.nextInstallment().amount()),
                view.status(),
                new InstallmentPlanResponse.MemberSummary(
                        view.attributedTo().id(), view.attributedTo().fullName()),
                detail.installments().stream()
                        .map(item -> new InstallmentItemResponse(
                                item.number(),
                                item.date(),
                                item.amount(),
                                item.status(),
                                item.transactionId()))
                        .toList());
    }
}
