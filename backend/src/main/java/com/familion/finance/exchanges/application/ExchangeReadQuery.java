package com.familion.finance.exchanges.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface ExchangeReadQuery {

    List<ExchangeReadRow> findPage(UUID familyId, UUID viewerUserId, Pageable pageable);

    long count(UUID familyId, UUID viewerUserId);

    List<ExchangePositionRow> findAllActiveChronological(UUID familyId, UUID viewerUserId);

    boolean hasActiveExchangeIn(UUID familyId, UUID exchangeId);

    List<UUID> findActiveTransactionIds(UUID familyId, UUID exchangeId);
}
