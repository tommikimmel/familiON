package com.familion.finance.recurring.application;

import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.recurring.application.api.RecurringDue;
import com.familion.finance.recurring.application.api.RecurringExpenseQueryApi;
import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.recurring.domain.RecurringAmountEstimator;
import com.familion.finance.recurring.domain.RecurringSchedule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurringExpenseQueryService implements RecurringExpenseQueryApi {

    private final RecurringReadQuery readQuery;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Clock clock;
    private final RecurringAmountEstimator estimator;

    public RecurringExpenseQueryService(
            RecurringReadQuery readQuery,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock) {
        this.readQuery = readQuery;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.clock = clock;
        this.estimator = new RecurringAmountEstimator();
    }

    @Override
    public List<RecurringDue> pendingDueBetween(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (to.isBefore(from)) {
            return List.of();
        }

        YearMonth startYm = YearMonth.from(from);
        YearMonth endYm = YearMonth.from(to);

        List<RecurringDueCandidateRow> candidates =
                readQuery.findAllActiveCandidates(startYm.atDay(1), endYm.atEndOfMonth());
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = candidates.stream().map(RecurringDueCandidateRow::id).toList();
        List<RecurringPaymentRow> payments =
                readQuery.findAllPaymentsUpTo(candidateIds, endYm.atEndOfMonth());

        Set<PaidMonthKey> paidMonths = new HashSet<>();
        Map<UUID, List<RecurringPaymentRow>> paymentsByRecurringId = new HashMap<>();
        for (RecurringPaymentRow payment : payments) {
            paidMonths.add(new PaidMonthKey(
                    payment.recurringExpenseId(), YearMonth.from(payment.transactionDate())));
            paymentsByRecurringId
                    .computeIfAbsent(payment.recurringExpenseId(), ignored -> new ArrayList<>())
                    .add(payment);
        }

        Map<UUID, FamilySettings> settingsByFamily = new HashMap<>();
        List<RecurringDue> dues = new ArrayList<>();

        for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
            LocalDate monthStart = ym.atDay(1);
            for (RecurringDueCandidateRow candidate : candidates) {
                if (!RecurringSchedule.isApplicable(
                        candidate.startDate(), candidate.endDate(), ym)) {
                    continue;
                }
                LocalDate dueDate = RecurringSchedule.dueDateForMonth(candidate.dueDay(), ym);
                if (dueDate.isBefore(from) || dueDate.isAfter(to)) {
                    continue;
                }
                if (paidMonths.contains(new PaidMonthKey(candidate.id(), ym))) {
                    continue;
                }

                FamilySettings settings = settingsByFamily.computeIfAbsent(
                        candidate.familyId(), familyQueryApi::getSettings);
                LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));

                BigDecimal lastPaidBefore = candidate.adjustmentRule() == AdjustmentRule.LAST_PAID
                        ? findLatestPaidBefore(paymentsByRecurringId.get(candidate.id()), monthStart)
                        : null;

                BigDecimal estimatedAmount = estimator.estimate(
                        candidate.adjustmentRule(),
                        candidate.baseAmount(),
                        candidate.currencyCode(),
                        candidate.startDate(),
                        ym,
                        today,
                        settings.referenceCurrency(),
                        settings.referenceRateSource(),
                        lastPaidBefore,
                        economicsApi);

                dues.add(new RecurringDue(
                        candidate.id(),
                        candidate.familyId(),
                        candidate.name(),
                        dueDate,
                        estimatedAmount,
                        candidate.currencyCode()));
            }
        }

        dues.sort(Comparator.comparing(RecurringDue::dueDate)
                .thenComparing(d -> d.name().toLowerCase(Locale.ROOT))
                .thenComparing(RecurringDue::recurringExpenseId));
        return List.copyOf(dues);
    }

    private static BigDecimal findLatestPaidBefore(
            List<RecurringPaymentRow> descendingPayments, LocalDate beforeDate) {
        if (descendingPayments == null || descendingPayments.isEmpty()) {
            return null;
        }
        for (RecurringPaymentRow payment : descendingPayments) {
            if (payment.transactionDate().isBefore(beforeDate)) {
                return payment.amount();
            }
        }
        return null;
    }

    private record PaidMonthKey(UUID recurringExpenseId, YearMonth month) {}
}
