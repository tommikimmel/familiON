package com.familion.finance.goals.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.View;
import com.familion.finance.goals.application.CreateContributionCommand;
import com.familion.finance.goals.application.CreateSavingsGoalCommand;
import com.familion.finance.goals.application.PatchSavingsGoalCommand;
import com.familion.finance.goals.application.SavingsGoalService;
import com.familion.finance.goals.domain.GoalStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/savings-goals")
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;
    private final CurrentUserProvider currentUserProvider;

    public SavingsGoalController(
            SavingsGoalService savingsGoalService, CurrentUserProvider currentUserProvider) {
        this.savingsGoalService = savingsGoalService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<SavingsGoalResponse> listSavingsGoals(
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(required = false) GoalStatus status) {
        return savingsGoalService.list(currentUserProvider.get(), view, status).stream()
                .map(SavingsGoalResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavingsGoalResponse createSavingsGoal(
            @Valid @RequestBody SavingsGoalCreateRequest request) {
        return SavingsGoalResponse.from(savingsGoalService.create(
                currentUserProvider.get(),
                new CreateSavingsGoalCommand(
                        request.name(),
                        request.scope(),
                        request.targetAmount(),
                        request.targetUnit(),
                        request.targetDate())));
    }

    @PatchMapping("/{id}")
    public SavingsGoalResponse patchSavingsGoal(
            @PathVariable UUID id, @Valid @RequestBody SavingsGoalPatchRequest request) {
        boolean targetDatePresent = request.targetDate() != null;
        LocalDate targetDate = targetDatePresent ? request.targetDate().orElse(null) : null;
        return SavingsGoalResponse.from(savingsGoalService.patch(
                currentUserProvider.get(),
                id,
                new PatchSavingsGoalCommand(
                        request.name(),
                        request.targetAmount(),
                        targetDatePresent,
                        targetDate,
                        request.status())));
    }

    @GetMapping("/{id}/contributions")
    public List<ContributionResponse> listContributions(@PathVariable UUID id) {
        return savingsGoalService.listContributions(currentUserProvider.get(), id).stream()
                .map(ContributionResponse::from)
                .toList();
    }

    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public ContributionResponse addContribution(
            @PathVariable UUID id, @Valid @RequestBody ContributionCreateRequest request) {
        return ContributionResponse.from(savingsGoalService.addContribution(
                currentUserProvider.get(),
                id,
                new CreateContributionCommand(
                        request.amount(),
                        request.currencyCode(),
                        request.contributionDate(),
                        request.notes(),
                        request.transactionId())));
    }

    @DeleteMapping("/{id}/contributions/{cid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContribution(@PathVariable UUID id, @PathVariable UUID cid) {
        savingsGoalService.deleteContribution(currentUserProvider.get(), id, cid);
    }
}
