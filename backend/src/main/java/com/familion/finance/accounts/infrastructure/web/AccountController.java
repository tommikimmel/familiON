package com.familion.finance.accounts.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.View;
import com.familion.finance.accounts.application.AccountService;
import com.familion.finance.accounts.application.CreateAccountCommand;
import com.familion.finance.accounts.application.UpdateAccountCommand;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserProvider currentUserProvider;

    public AccountController(
            AccountService accountService, CurrentUserProvider currentUserProvider) {
        this.accountService = accountService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<AccountResponse> listAccounts(
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return accountService
                .listAccounts(currentUserProvider.get(), view, includeInactive)
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody AccountCreateRequest request) {
        return AccountResponse.from(accountService.createAccount(
                currentUserProvider.get(),
                new CreateAccountCommand(
                        request.name(),
                        request.accountType(),
                        request.currencyCode(),
                        request.initialBalance(),
                        request.ownerUserId(),
                        request.creditLimit(),
                        request.closingDay(),
                        request.dueDay())));
    }

    @PatchMapping("/{id}")
    public AccountResponse updateAccount(
            @PathVariable UUID id, @Valid @RequestBody AccountUpdateRequest request) {
        boolean ownerPresent = request.ownerUserId() != null;
        UUID ownerUserId = ownerPresent ? request.ownerUserId().orElse(null) : null;
        boolean creditLimitPresent = request.creditLimit() != null;
        BigDecimal creditLimit =
                creditLimitPresent ? request.creditLimit().orElse(null) : null;

        return AccountResponse.from(accountService.updateAccount(
                currentUserProvider.get(),
                id,
                new UpdateAccountCommand(
                        request.name(),
                        request.accountType(),
                        request.initialBalance(),
                        ownerPresent,
                        ownerUserId,
                        creditLimitPresent,
                        creditLimit,
                        request.closingDay(),
                        request.dueDay())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable UUID id) {
        accountService.deleteAccount(currentUserProvider.get(), id);
    }
}
