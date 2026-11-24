package com.familion.finance.recurring.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.finance.recurring.application.ConfirmPaymentCommand;
import com.familion.finance.recurring.application.CreateRecurringExpenseCommand;
import com.familion.finance.recurring.application.RecurringExpenseService;
import com.familion.finance.recurring.application.UpdateRecurringExpenseCommand;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/recurring-expenses")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;
    private final CurrentUserProvider currentUserProvider;

    public RecurringExpenseController(
            RecurringExpenseService recurringExpenseService,
            CurrentUserProvider currentUserProvider) {
        this.recurringExpenseService = recurringExpenseService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<RecurringExpenseResponse> listRecurringExpenses(
            @RequestParam(required = false) YearMonth month,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return recurringExpenseService
                .list(currentUserProvider.get(), month, includeInactive)
                .stream()
                .map(RecurringExpenseResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO')")
    public RecurringExpenseResponse createRecurringExpense(
            @Valid @RequestBody RecurringExpenseCreateRequest request) {
        return RecurringExpenseResponse.from(recurringExpenseService.create(
                currentUserProvider.get(),
                new CreateRecurringExpenseCommand(
                        request.name(),
                        request.categoryId(),
                        request.accountId(),
                        request.scope(),
                        request.baseAmount(),
                        request.currencyCode(),
                        request.dueDay(),
                        request.adjustmentRule(),
                        request.startDate(),
                        request.endDate())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADULTO')")
    public RecurringExpenseResponse updateRecurringExpense(
            @PathVariable UUID id, @Valid @RequestBody RecurringExpensePatchRequest request) {
        return RecurringExpenseResponse.from(recurringExpenseService.update(
                currentUserProvider.get(),
                id,
                new UpdateRecurringExpenseCommand(
                        request.name(),
                        request.categoryId(),
                        request.accountIdPresent(),
                        request.accountId(),
                        request.scope(),
                        request.baseAmount(),
                        request.currencyCode(),
                        request.dueDay(),
                        request.adjustmentRule(),
                        request.startDate(),
                        request.endDatePresent(),
                        request.endDate(),
                        request.isActive())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADULTO')")
    public void deleteRecurringExpense(@PathVariable UUID id) {
        recurringExpenseService.deactivate(currentUserProvider.get(), id);
    }

    @PostMapping("/{id}/confirm-payment")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringExpenseResponse confirmPayment(
            @PathVariable UUID id, @Valid @RequestBody ConfirmPaymentRequest request) {
        return RecurringExpenseResponse.from(recurringExpenseService.confirmPayment(
                currentUserProvider.get(),
                id,
                new ConfirmPaymentCommand(
                        request.month(),
                        request.amount(),
                        request.accountId(),
                        request.paymentDate())));
    }
}
