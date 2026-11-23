package com.familion.finance.accounts.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceQuery {

    List<AccountView> listWithBalances(
            UUID familyId, UUID userId, AccountListingScope scope, boolean includeInactive);

    Optional<BigDecimal> computeCurrentBalance(UUID accountId);
}
