package com.familion.family.allowances.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.family.allowances.application.AllowanceService;
import com.familion.family.allowances.application.CreateAllowanceCommand;
import com.familion.family.allowances.application.DisburseAllowanceCommand;
import com.familion.family.allowances.application.UpdateAllowanceCommand;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/family/allowances")
public class AllowanceController {

    private final AllowanceService allowanceService;
    private final CurrentUserProvider currentUserProvider;

    public AllowanceController(
            AllowanceService allowanceService, CurrentUserProvider currentUserProvider) {
        this.allowanceService = allowanceService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<AllowanceResponse> listAllowances(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return allowanceService.list(currentUserProvider.get(), includeInactive).stream()
                .map(AllowanceResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO')")
    public AllowanceResponse createAllowance(@Valid @RequestBody AllowanceCreateRequest request) {
        return AllowanceResponse.from(allowanceService.create(
                currentUserProvider.get(),
                new CreateAllowanceCommand(
                        request.childUserId(),
                        request.sourceAccountId(),
                        request.targetAccountId(),
                        request.amount(),
                        request.currencyCode(),
                        request.frequency(),
                        request.dayOfPeriod(),
                        request.adjustmentRule())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADULTO')")
    public AllowanceResponse updateAllowance(
            @PathVariable UUID id, @Valid @RequestBody AllowancePatchRequest request) {
        return AllowanceResponse.from(allowanceService.update(
                currentUserProvider.get(),
                id,
                new UpdateAllowanceCommand(
                        request.childUserId(),
                        request.sourceAccountIdPresent(),
                        request.sourceAccountId(),
                        request.targetAccountId(),
                        request.amount(),
                        request.currencyCode(),
                        request.frequency(),
                        request.dayOfPeriod(),
                        request.adjustmentRule(),
                        request.isActive())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADULTO')")
    public void deleteAllowance(@PathVariable UUID id) {
        allowanceService.deactivate(currentUserProvider.get(), id);
    }

    @PostMapping("/{id}/disburse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO')")
    public DisburseAllowanceResponse disburseAllowance(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DisburseAllowanceRequest request) {
        DisburseAllowanceCommand command = request == null
                ? new DisburseAllowanceCommand(null, null)
                : new DisburseAllowanceCommand(request.date(), request.amount());
        return DisburseAllowanceResponse.from(
                allowanceService.disburse(currentUserProvider.get(), id, command));
    }
}
