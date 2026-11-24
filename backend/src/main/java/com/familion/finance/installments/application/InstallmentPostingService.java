package com.familion.finance.installments.application;

import com.familion.finance.installments.domain.InstallmentPlan;
import com.familion.finance.installments.infrastructure.persistence.InstallmentPlanRepository;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallmentPostingService {

    public static final ZoneId BUENOS_AIRES_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    public record PostingResult(int postedInstallments, int completedPlans) {}

    private final InstallmentPlanReadQuery readQuery;
    private final InstallmentPlanRepository planRepository;
    private final TransactionCommandApi transactionCommandApi;
    private final Clock clock;

    public InstallmentPostingService(
            InstallmentPlanReadQuery readQuery,
            InstallmentPlanRepository planRepository,
            TransactionCommandApi transactionCommandApi,
            Clock clock) {
        this.readQuery = readQuery;
        this.planRepository = planRepository;
        this.transactionCommandApi = transactionCommandApi;
        this.clock = clock;
    }

    @Transactional
    public PostingResult postDueInstallments() {
        return postDueInstallments(clock);
    }

    @Transactional
    public PostingResult postDueInstallments(Clock effectiveClock) {
        Objects.requireNonNull(effectiveClock, "effectiveClock must not be null");
        LocalDate today = LocalDate.now(effectiveClock.withZone(BUENOS_AIRES_ZONE));
        return postDueInstallments(today);
    }

    @Transactional
    public PostingResult postDueInstallments(LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        List<DueInstallmentRow> dueRows = readQuery.findDueProjectedInstallments(asOfDate);
        for (DueInstallmentRow row : dueRows) {
            transactionCommandApi.changeStatus(
                    row.familyId(), row.transactionId(), TransactionStatus.POSTED);
        }

        List<UUID> completablePlanIds = readQuery.findActivePlansWithAllPostedInstallments();
        int completedCount = 0;
        for (UUID planId : completablePlanIds) {
            InstallmentPlan plan = planRepository.findById(planId).orElse(null);
            if (plan != null && plan.isActive()) {
                plan.markCompleted();
                planRepository.saveAndFlush(plan);
                completedCount++;
            }
        }

        return new PostingResult(dueRows.size(), completedCount);
    }
}
