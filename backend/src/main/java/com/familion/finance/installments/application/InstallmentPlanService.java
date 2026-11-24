package com.familion.finance.installments.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.installments.domain.CancelRemainingMode;
import com.familion.finance.installments.domain.InstallmentMetricsCalculator;
import com.familion.finance.installments.domain.InstallmentPlan;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import com.familion.finance.installments.domain.InstallmentScheduleCalculator;
import com.familion.finance.installments.infrastructure.persistence.InstallmentPlanRepository;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallmentPlanService {

    static final String CREDIT_CARD_ACCOUNT_TYPE = "CREDIT_CARD";
    static final String EXPENSE_CATEGORY_KIND = "EXPENSE";
    static final String IPC_MENSUAL_INDEX = "IPC_MENSUAL";
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int MAX_CURRENCY_LENGTH = 10;

    private final InstallmentPlanRepository planRepository;
    private final InstallmentPlanReadQuery readQuery;
    private final TransactionCommandApi transactionCommandApi;
    private final AccountQueryApi accountQueryApi;
    private final CategoryQueryApi categoryQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Clock clock;
    private final InstallmentScheduleCalculator scheduleCalculator;
    private final InstallmentMetricsCalculator metricsCalculator;
    private final InstallmentPlanViewAssembler viewAssembler;

    public InstallmentPlanService(
            InstallmentPlanRepository planRepository,
            InstallmentPlanReadQuery readQuery,
            TransactionCommandApi transactionCommandApi,
            AccountQueryApi accountQueryApi,
            CategoryQueryApi categoryQueryApi,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock) {
        this.planRepository = planRepository;
        this.readQuery = readQuery;
        this.transactionCommandApi = transactionCommandApi;
        this.accountQueryApi = accountQueryApi;
        this.categoryQueryApi = categoryQueryApi;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.clock = clock;
        this.scheduleCalculator = new InstallmentScheduleCalculator();
        this.metricsCalculator = new InstallmentMetricsCalculator();
        this.viewAssembler =
                new InstallmentPlanViewAssembler(this.scheduleCalculator, this.metricsCalculator);
    }

    @Transactional
    public InstallmentPlanView create(CurrentUser user, CreateInstallmentPlanCommand command) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(command, "command must not be null");

        String description = normalizeDescription(command.description());
        String currencyCode = normalizeCurrency(command.currencyCode());
        if (command.purchaseDate() == null) {
            throw new BusinessRuleException("Purchase date is required");
        }
        if (command.amountMode() == null) {
            throw new BusinessRuleException("Amount mode is required");
        }

        UUID attributedTo = command.attributedToUserId() != null
                ? command.attributedToUserId()
                : user.userId();
        if (!attributedTo.equals(user.userId()) && !user.isAdult()) {
            throw new ForbiddenException(
                    "Only adult users can attribute installment plans to another member");
        }

        AccountRef creditCard =
                accountQueryApi.requireUsable(user, command.creditCardAccountId());
        if (!CREDIT_CARD_ACCOUNT_TYPE.equals(creditCard.accountType())) {
            throw new BusinessRuleException(
                    "Installment plans require a CREDIT_CARD account");
        }

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        validateEnabledCurrency(currencyCode, settings.enabledCurrencies());

        MemberRef attributedMember = familyQueryApi.requireMember(user.familyId(), attributedTo);
        CategoryRef category = categoryQueryApi.requireUsable(
                user.familyId(), command.categoryId(), EXPENSE_CATEGORY_KIND);

        BigDecimal resolvedCashPrice = resolveCashPrice(command.hasInterest(), command.cashPrice());

        InstallmentScheduleCalculator.PlanSchedule schedule = scheduleCalculator.calculate(
                command.purchaseDate(),
                command.totalInstallments(),
                command.amountMode(),
                command.amount(),
                creditCard.closingDay(),
                creditCard.dueDay());

        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        boolean allPosted =
                schedule.installments().stream().allMatch(item -> !item.date().isAfter(today));
        InstallmentPlanStatus initialStatus =
                allPosted ? InstallmentPlanStatus.COMPLETED : InstallmentPlanStatus.ACTIVE;

        InstallmentPlan plan = InstallmentPlan.create(
                user.familyId(),
                user.userId(),
                creditCard.id(),
                category.id(),
                description,
                currencyCode,
                command.totalInstallments(),
                command.hasInterest(),
                resolvedCashPrice,
                schedule.financedTotal(),
                schedule.installmentAmount(),
                command.purchaseDate(),
                schedule.firstBillingDate(),
                initialStatus);

        InstallmentPlan saved = planRepository.saveAndFlush(plan);

        int paidInstallments = 0;
        BigDecimal remainingAmount = BigDecimal.ZERO;
        List<InstallmentMetricsCalculator.ProjectedInstallment> projectedInstallments =
                new ArrayList<>();
        InstallmentPlanView.NextInstallmentSummary nextInstallment = null;

        for (InstallmentScheduleCalculator.ScheduledInstallment item : schedule.installments()) {
            TransactionStatus txStatus = !item.date().isAfter(today)
                    ? TransactionStatus.POSTED
                    : TransactionStatus.PROJECTED;

            transactionCommandApi.record(new NewTransactionCommand(
                    user.familyId(),
                    user.userId(),
                    attributedTo,
                    creditCard.id(),
                    category.id(),
                    TransactionType.EXPENSE,
                    Scope.PERSONAL,
                    txStatus,
                    item.amount(),
                    currencyCode,
                    item.date(),
                    buildInstallmentDescription(description, item.number(), command.totalInstallments()),
                    null,
                    saved.getId(),
                    item.number(),
                    null,
                    null,
                    null,
                    null,
                    null));

            if (txStatus == TransactionStatus.POSTED) {
                paidInstallments++;
            } else {
                remainingAmount = remainingAmount.add(item.amount());
                projectedInstallments.add(new InstallmentMetricsCalculator.ProjectedInstallment(
                        item.date(), item.amount()));
                if (nextInstallment == null) {
                    nextInstallment = new InstallmentPlanView.NextInstallmentSummary(
                            item.number(), item.date(), item.amount());
                }
            }
        }

        InstallmentMetricsCalculator.SurchargeResult surcharge =
                metricsCalculator.calculateSurcharge(
                        saved.isHasInterest(), saved.getCashPrice(), saved.getFinancedTotal());
        BigDecimal latestIpc = economicsApi.latestValue(IPC_MENSUAL_INDEX).orElse(null);
        BigDecimal realRemainingAmount = metricsCalculator.calculateRealRemainingAmount(
                projectedInstallments, today, latestIpc);

        return new InstallmentPlanView(
                saved.getId(),
                saved.getDescription(),
                new InstallmentPlanView.CategorySummary(
                        category.id(), category.name(), category.icon(), category.colorHex()),
                new InstallmentPlanView.AccountSummary(creditCard.id(), creditCard.name()),
                saved.getCurrencyCode(),
                saved.getTotalInstallments(),
                paidInstallments,
                saved.isHasInterest(),
                saved.getCashPrice(),
                saved.getFinancedTotal(),
                saved.getInstallmentAmount(),
                surcharge.surchargeAmount(),
                surcharge.surchargePct(),
                saved.getPurchaseDate(),
                saved.getFirstBillingDate(),
                schedule.lastBillingDate(),
                Money.scale(remainingAmount),
                realRemainingAmount,
                nextInstallment,
                saved.getStatus(),
                new InstallmentPlanView.MemberSummary(
                        attributedMember.userId(), attributedMember.fullName()));
    }

    @Transactional
    public InstallmentPlanView cancelRemaining(
            CurrentUser user, UUID planId, CancelRemainingCommand command) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (command.mode() == null) {
            throw new BusinessRuleException("Cancel mode is required");
        }

        InstallmentPlan plan = planRepository
                .findByIdAndFamilyId(planId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Installment plan not found"));

        if (!user.isAdult() && !user.userId().equals(plan.getUserId())) {
            throw new ForbiddenException(
                    "Only the plan author or an adult can cancel or prepay remaining installments");
        }

        if (!plan.isActive()) {
            throw new ConflictException("Installment plan is already closed");
        }

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));

        List<InstallmentTransactionRow> installments =
                readQuery.findInstallmentsByPlanIds(user.familyId(), List.of(planId));
        List<InstallmentTransactionRow> projected = installments.stream()
                .filter(tx -> tx.status() == TransactionStatus.PROJECTED)
                .toList();

        if (command.mode() == CancelRemainingMode.CANCEL) {
            for (InstallmentTransactionRow tx : projected) {
                transactionCommandApi.changeStatus(
                        user.familyId(), tx.transactionId(), TransactionStatus.CANCELLED);
            }
            plan.markCancelled();
        } else {
            LocalDate prepayDate = command.prepayDate() != null ? command.prepayDate() : today;
            for (InstallmentTransactionRow tx : projected) {
                transactionCommandApi.updateAmountAndDate(
                        user.familyId(), tx.transactionId(), tx.amount(), prepayDate);
                transactionCommandApi.changeStatus(
                        user.familyId(), tx.transactionId(), TransactionStatus.POSTED);
            }
            plan.markCompleted();
        }

        planRepository.saveAndFlush(plan);

        InstallmentPlanReadRow refreshedRow = readQuery
                .findPlanById(user.familyId(), planId)
                .orElseThrow(() -> new NotFoundException("Installment plan not found"));
        List<InstallmentTransactionRow> refreshedInstallments =
                readQuery.findInstallmentsByPlanIds(user.familyId(), List.of(planId));
        BigDecimal latestIpc = economicsApi.latestValue(IPC_MENSUAL_INDEX).orElse(null);

        return viewAssembler.toView(refreshedRow, refreshedInstallments, today, latestIpc);
    }

    private static BigDecimal resolveCashPrice(boolean hasInterest, BigDecimal cashPrice) {
        if (!hasInterest || cashPrice == null) {
            return null;
        }
        if (cashPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Cash price must be greater than zero");
        }
        BigDecimal scaled = Money.scale(cashPrice);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Cash price must be greater than zero");
        }
        return scaled;
    }

    private static void validateEnabledCurrency(
            String currencyCode, List<String> enabledCurrencies) {
        boolean enabled = enabledCurrencies != null
                && enabledCurrencies.stream().anyMatch(c -> currencyCode.equalsIgnoreCase(c));
        if (!enabled) {
            throw new BusinessRuleException(
                    "Currency " + currencyCode + " is not enabled for this family");
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new BusinessRuleException("Description is required");
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessRuleException(
                    "Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new BusinessRuleException("Currency code is required");
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_CURRENCY_LENGTH) {
            throw new BusinessRuleException(
                    "Currency code must be at most " + MAX_CURRENCY_LENGTH + " characters");
        }
        return normalized;
    }

    private static String buildInstallmentDescription(
            String planDescription, int number, int total) {
        return planDescription + " (" + number + "/" + total + ")";
    }
}
