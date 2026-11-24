package com.familion.finance.recurring.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RecurringReadQuery {

    List<RecurringReadRow> findVisibleForUser(
            UUID familyId, UUID userId, boolean adult, boolean includeInactive);

    Optional<RecurringReadRow> findById(UUID familyId, UUID recurringExpenseId);

    Map<UUID, RecurringPaymentRow> findPaymentsInRange(
            Collection<UUID> recurringExpenseIds, LocalDate from, LocalDate to);

    Map<UUID, BigDecimal> findLatestPaidAmountBefore(
            Collection<UUID> recurringExpenseIds, LocalDate beforeDate);

    boolean existsPaymentInRange(
            UUID familyId, UUID recurringExpenseId, LocalDate from, LocalDate to);

    List<RecurringDueCandidateRow> findAllActiveCandidates(
            LocalDate firstMonthStart, LocalDate lastMonthEnd);

    List<RecurringPaymentRow> findAllPaymentsUpTo(
            Collection<UUID> recurringExpenseIds, LocalDate upToDate);
}
