package com.familion.finance.transactions.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TransactionReadQuery {

    Optional<TransactionReadRow> findById(UUID familyId, UUID transactionId);

    Map<YearMonth, BigDecimal> sumPostedIncomeByMonth(
            UUID familyId, UUID viewerUserId, Set<YearMonth> months);
}
