package com.familion.finance.transactions.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface TransactionReadQuery {

    Optional<TransactionReadRow> findById(UUID familyId, UUID transactionId);

    List<TransactionReadRow> findPage(
            UUID familyId, UUID viewerUserId, TransactionFilter filter, Pageable pageable);

    long count(UUID familyId, UUID viewerUserId, TransactionFilter filter);

    Map<YearMonth, BigDecimal> sumPostedIncomeByMonth(
            UUID familyId, UUID viewerUserId, Set<YearMonth> months);
}
