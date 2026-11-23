package com.familion.finance.accounts.application;

import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.accounts.domain.Account;
import com.familion.finance.accounts.infrastructure.persistence.AccountRepository;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService implements AccountQueryApi {

    private final AccountRepository accountRepository;
    private final AccountBalanceQuery accountBalanceQuery;

    public AccountQueryService(
            AccountRepository accountRepository, AccountBalanceQuery accountBalanceQuery) {
        this.accountRepository = accountRepository;
        this.accountBalanceQuery = accountBalanceQuery;
    }

    @Override
    public AccountRef requireUsable(CurrentUser user, UUID accountId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        Account account = accountRepository
                .findByIdAndFamilyId(accountId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (!account.isUsable()) {
            throw new NotFoundException("Account not found");
        }

        if (!user.isAdult() && !account.isOwnedBy(user.userId())) {
            throw new ForbiddenException("Child users can only use their own accounts");
        }

        return new AccountRef(
                account.getId(),
                account.getFamilyId(),
                account.getOwnerUserId(),
                account.getName(),
                account.getAccountType().name(),
                account.getCurrencyCode(),
                account.getClosingDay(),
                account.getDueDay());
    }

    @Override
    public BigDecimal currentBalance(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return accountBalanceQuery
                .computeCurrentBalance(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
