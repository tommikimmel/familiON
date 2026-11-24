package com.familion.finance.installments.application;

import com.familion.finance.installments.domain.InstallmentPlanStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentPlanReadQuery {

    List<InstallmentPlanReadRow> findPlans(
            UUID familyId, UUID viewerUserId, InstallmentPlanStatus status);

    Optional<InstallmentPlanReadRow> findPlanById(UUID familyId, UUID planId);

    List<InstallmentTransactionRow> findInstallmentsByPlanIds(
            UUID familyId, Collection<UUID> planIds);

    List<CommitmentRow> findMonthlyCommitment(
            UUID familyId, UUID viewerUserId, LocalDate fromDate, LocalDate toDate);

    List<DueInstallmentRow> findDueProjectedInstallments(LocalDate asOfDate);

    List<UUID> findActivePlansWithAllPostedInstallments();
}
