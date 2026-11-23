package com.familion.finance.transactions.application;

import com.familion.common.security.View;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Lens;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransactionFilter(
        View view,
        Lens lens,
        LocalDate from,
        LocalDate to,
        List<TransactionType> types,
        UUID categoryId,
        UUID accountId,
        UUID attributedToUserId,
        String currency,
        List<TransactionStatus> statuses,
        String q) {}
