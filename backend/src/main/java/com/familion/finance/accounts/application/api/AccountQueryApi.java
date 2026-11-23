package com.familion.finance.accounts.application.api;

import com.familion.common.security.CurrentUser;
import java.math.BigDecimal;
import java.util.UUID;

public interface AccountQueryApi {

    AccountRef requireUsable(CurrentUser user, UUID accountId);

    BigDecimal currentBalance(UUID accountId);
}
