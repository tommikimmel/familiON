package com.familion.finance.transactions.infrastructure.web;

import com.familion.finance.transactions.application.TransactionItemView;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Lens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionItemResponse(
        UUID id,
        TransactionType type,
        Scope scope,
        TransactionStatus status,
        BigDecimal amount,
        String currencyCode,
        LocalDate transactionDate,
        String description,
        String notes,
        CategorySummary category,
        AccountSummary account,
        UserSummary paidBy,
        UserSummary attributedTo,
        InstallmentSummary installment,
        Lens lens,
        BigDecimal lensAmount,
        String lensUnit,
        boolean lensApproximated) {

    public record CategorySummary(UUID id, String name, String icon, String colorHex) {}

    public record AccountSummary(UUID id, String name, String accountType) {}

    public record UserSummary(UUID id, String fullName) {}

    public record InstallmentSummary(UUID planId, Integer number, Integer total) {}

    public static TransactionItemResponse from(TransactionItemView view) {
        CategorySummary category = view.category() == null
                ? null
                : new CategorySummary(
                        view.category().id(),
                        view.category().name(),
                        view.category().icon(),
                        view.category().colorHex());
        AccountSummary account = new AccountSummary(
                view.account().id(), view.account().name(), view.account().accountType());
        UserSummary paidBy = new UserSummary(view.paidBy().id(), view.paidBy().fullName());
        UserSummary attributedTo =
                new UserSummary(view.attributedTo().id(), view.attributedTo().fullName());
        InstallmentSummary installment = view.installment() == null
                ? null
                : new InstallmentSummary(
                        view.installment().planId(),
                        view.installment().number(),
                        view.installment().total());

        return new TransactionItemResponse(
                view.id(),
                view.type(),
                view.scope(),
                view.status(),
                view.amount(),
                view.currencyCode(),
                view.transactionDate(),
                view.description(),
                view.notes(),
                category,
                account,
                paidBy,
                attributedTo,
                installment,
                view.lens(),
                view.lensAmount(),
                view.lensUnit(),
                view.lensApproximated());
    }
}
