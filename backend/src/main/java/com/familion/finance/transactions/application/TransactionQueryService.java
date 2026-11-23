package com.familion.finance.transactions.application;

import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.View;
import com.familion.common.security.ViewAccess;
import com.familion.common.security.VisibilityPolicy;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.transactions.domain.Lens;
import com.familion.finance.transactions.domain.LensCalculator;
import com.familion.finance.transactions.domain.LensResult;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

    static final String IPC_ACUMULADO_INDEX = "IPC_ACUMULADO";

    private final TransactionReadQuery readQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final LensCalculator lensCalculator;

    public TransactionQueryService(
            TransactionReadQuery readQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi) {
        this.readQuery = readQuery;
        this.visibilityPolicy = visibilityPolicy;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.lensCalculator = new LensCalculator();
    }

    public TransactionItemView getById(
            CurrentUser user, UUID transactionId, Lens requestedLens, View requestedView) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");

        TransactionReadRow row = readQuery
                .findById(user.familyId(), transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        boolean canSeeFamily =
                user.isAdult() || user.visibility() == FamilyVisibility.FAMILY_FULL;
        boolean isOwn =
                user.userId().equals(row.attributedToId()) || user.userId().equals(row.paidById());
        if (!canSeeFamily && !isOwn) {
            throw new ForbiddenException("User is not allowed to view this transaction");
        }

        Lens lens = requestedLens != null ? requestedLens : Lens.NOMINAL;
        UUID incomeViewerUserId = null;
        if (lens == Lens.INCOME_PCT) {
            if (requestedView != null) {
                ViewAccess access = visibilityPolicy.resolveForListing(user, requestedView);
                incomeViewerUserId = access == ViewAccess.OWN ? user.userId() : null;
            } else {
                incomeViewerUserId = canSeeFamily && !user.userId().equals(row.attributedToId())
                        ? null
                        : user.userId();
            }
        }

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        BigDecimal latestIpc = lens == Lens.CONSTANT_BASE
                ? economicsApi.latestValue(IPC_ACUMULADO_INDEX).orElse(null)
                : null;
        Map<YearMonth, BigDecimal> incomeByMonth = lens == Lens.INCOME_PCT
                ? readQuery.sumPostedIncomeByMonth(
                        user.familyId(),
                        incomeViewerUserId,
                        Set.of(YearMonth.from(row.transactionDate())))
                : Map.of();

        return toItemView(row, lens, settings, latestIpc, incomeByMonth);
    }

    TransactionItemView getItemByIdInternal(
            UUID familyId, UUID transactionId, Lens lens, FamilySettings settings) {
        TransactionReadRow row = readQuery
                .findById(familyId, transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        return toItemView(row, lens, settings, null, Map.of());
    }

    TransactionItemView toItemView(
            TransactionReadRow row,
            Lens lens,
            FamilySettings settings,
            BigDecimal latestIpc,
            Map<YearMonth, BigDecimal> incomeByMonth) {
        BigDecimal monthlyIncome =
                lens == Lens.INCOME_PCT
                        ? incomeByMonth.get(YearMonth.from(row.transactionDate()))
                        : null;
        LensResult lensResult = lensCalculator.calculate(
                lens,
                settings.baseCurrency(),
                settings.referenceCurrency(),
                row.amount(),
                row.snapshotBaseCurrencyAmount(),
                row.snapshotAmountInReference(),
                row.snapshotReferenceCurrency(),
                row.snapshotIpcIndexValue(),
                latestIpc,
                monthlyIncome);

        TransactionItemView.CategorySummary category = row.categoryId() == null
                ? null
                : new TransactionItemView.CategorySummary(
                        row.categoryId(),
                        row.categoryName(),
                        row.categoryIcon(),
                        row.categoryColorHex());

        TransactionItemView.AccountSummary account = new TransactionItemView.AccountSummary(
                row.accountId(), row.accountName(), row.accountType());

        TransactionItemView.UserSummary paidBy =
                new TransactionItemView.UserSummary(row.paidById(), row.paidByFullName());

        TransactionItemView.UserSummary attributedTo =
                new TransactionItemView.UserSummary(
                        row.attributedToId(), row.attributedToFullName());

        TransactionItemView.InstallmentSummary installment = row.installmentPlanId() == null
                ? null
                : new TransactionItemView.InstallmentSummary(
                        row.installmentPlanId(), row.installmentNumber(), row.installmentTotal());

        return new TransactionItemView(
                row.id(),
                row.type(),
                row.scope(),
                row.status(),
                row.amount(),
                row.currencyCode(),
                row.transactionDate(),
                row.description(),
                row.notes(),
                category,
                account,
                paidBy,
                attributedTo,
                installment,
                lensResult.lens(),
                lensResult.lensAmount(),
                lensResult.lensUnit(),
                lensResult.lensApproximated());
    }
}
