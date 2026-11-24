package com.familion.finance.installments.application;

import com.familion.common.error.BusinessRuleException;
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
import com.familion.finance.installments.domain.InstallmentMetricsCalculator;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import com.familion.finance.installments.domain.InstallmentScheduleCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InstallmentPlanQueryService {

    static final String IPC_MENSUAL_INDEX = "IPC_MENSUAL";
    static final int MAX_COMMITMENT_MONTHS = 24;

    private final InstallmentPlanReadQuery readQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Clock clock;
    private final InstallmentPlanViewAssembler viewAssembler;

    public InstallmentPlanQueryService(
            InstallmentPlanReadQuery readQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock) {
        this.readQuery = readQuery;
        this.visibilityPolicy = visibilityPolicy;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.clock = clock;
        this.viewAssembler = new InstallmentPlanViewAssembler(
                new InstallmentScheduleCalculator(), new InstallmentMetricsCalculator());
    }

    public List<InstallmentPlanView> list(
            CurrentUser user, InstallmentPlanStatus status, View requestedView) {
        Objects.requireNonNull(user, "user must not be null");

        View view = requestedView != null ? requestedView : View.PERSONAL;
        ViewAccess access = visibilityPolicy.resolveForListing(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;

        List<InstallmentPlanReadRow> rows =
                readQuery.findPlans(user.familyId(), viewerUserId, status);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> planIds = rows.stream().map(InstallmentPlanReadRow::id).toList();
        Map<UUID, List<InstallmentTransactionRow>> txByPlan = readQuery
                .findInstallmentsByPlanIds(user.familyId(), planIds)
                .stream()
                .collect(Collectors.groupingBy(InstallmentTransactionRow::installmentPlanId));

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        BigDecimal latestIpc = economicsApi.latestValue(IPC_MENSUAL_INDEX).orElse(null);

        return rows.stream()
                .map(row -> viewAssembler.toView(
                        row, txByPlan.getOrDefault(row.id(), List.of()), today, latestIpc))
                .toList();
    }

    public InstallmentPlanDetailView getById(CurrentUser user, UUID planId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(planId, "planId must not be null");

        InstallmentPlanReadRow row = readQuery
                .findPlanById(user.familyId(), planId)
                .orElseThrow(() -> new NotFoundException("Installment plan not found"));

        List<InstallmentTransactionRow> installments =
                readQuery.findInstallmentsByPlanIds(user.familyId(), List.of(planId));

        UUID attributedToId = installments.isEmpty()
                ? row.userId()
                : installments.getFirst().attributedToUserId();
        boolean canSeeFamily =
                user.isAdult() || user.visibility() == FamilyVisibility.FAMILY_FULL;
        boolean isOwn =
                user.userId().equals(row.userId()) || user.userId().equals(attributedToId);
        if (!canSeeFamily && !isOwn) {
            throw new ForbiddenException("User is not allowed to view this installment plan");
        }

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        BigDecimal latestIpc = economicsApi.latestValue(IPC_MENSUAL_INDEX).orElse(null);

        return viewAssembler.toDetailView(row, installments, today, latestIpc);
    }

    public List<InstallmentCommitmentView> commitment(
            CurrentUser user, int requestedMonths, View requestedView) {
        Objects.requireNonNull(user, "user must not be null");
        if (requestedMonths < 1) {
            throw new BusinessRuleException("Months must be at least 1");
        }
        int months = Math.min(requestedMonths, MAX_COMMITMENT_MONTHS);

        View view = requestedView != null ? requestedView : View.PERSONAL;
        ViewAccess access = visibilityPolicy.resolveForTotals(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        YearMonth startMonth = YearMonth.from(today);
        LocalDate fromDate = startMonth.atDay(1);
        LocalDate toDate = startMonth.plusMonths(months - 1L).atEndOfMonth();

        return readQuery
                .findMonthlyCommitment(user.familyId(), viewerUserId, fromDate, toDate)
                .stream()
                .map(row -> new InstallmentCommitmentView(
                        row.month(), row.amount(), row.currencyCode()))
                .toList();
    }
}
