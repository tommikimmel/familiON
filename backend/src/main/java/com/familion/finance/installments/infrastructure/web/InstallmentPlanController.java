package com.familion.finance.installments.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.View;
import com.familion.finance.installments.application.CancelRemainingCommand;
import com.familion.finance.installments.application.CreateInstallmentPlanCommand;
import com.familion.finance.installments.application.InstallmentPlanQueryService;
import com.familion.finance.installments.application.InstallmentPlanService;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/installment-plans")
public class InstallmentPlanController {

    private final InstallmentPlanService planService;
    private final InstallmentPlanQueryService queryService;
    private final CurrentUserProvider currentUserProvider;

    public InstallmentPlanController(
            InstallmentPlanService planService,
            InstallmentPlanQueryService queryService,
            CurrentUserProvider currentUserProvider) {
        this.planService = planService;
        this.queryService = queryService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstallmentPlanResponse createPlan(
            @Valid @RequestBody InstallmentPlanCreateRequest request) {
        return InstallmentPlanResponse.from(planService.create(
                currentUserProvider.get(),
                new CreateInstallmentPlanCommand(
                        request.description(),
                        request.categoryId(),
                        request.creditCardAccountId(),
                        request.currencyCode(),
                        request.totalInstallments(),
                        request.amountMode(),
                        request.amount(),
                        request.hasInterest(),
                        request.cashPrice(),
                        request.purchaseDate(),
                        request.attributedToUserId())));
    }

    @GetMapping
    public List<InstallmentPlanResponse> listPlans(
            @RequestParam(required = false) InstallmentPlanStatus status,
            @RequestParam(defaultValue = "PERSONAL") View view) {
        return queryService.list(currentUserProvider.get(), status, view).stream()
                .map(InstallmentPlanResponse::from)
                .toList();
    }

    @GetMapping("/commitment")
    public List<InstallmentCommitmentResponse> getCommitment(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(defaultValue = "PERSONAL") View view) {
        return queryService.commitment(currentUserProvider.get(), months, view).stream()
                .map(InstallmentCommitmentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public InstallmentPlanDetailResponse getPlan(@PathVariable UUID id) {
        return InstallmentPlanDetailResponse.from(
                queryService.getById(currentUserProvider.get(), id));
    }

    @PostMapping("/{id}/cancel-remaining")
    public InstallmentPlanResponse cancelRemaining(
            @PathVariable UUID id, @Valid @RequestBody CancelRemainingRequest request) {
        return InstallmentPlanResponse.from(planService.cancelRemaining(
                currentUserProvider.get(),
                id,
                new CancelRemainingCommand(request.mode(), request.prepayDate())));
    }
}
