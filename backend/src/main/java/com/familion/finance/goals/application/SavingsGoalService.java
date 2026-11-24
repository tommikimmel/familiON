package com.familion.finance.goals.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.View;
import com.familion.common.security.VisibilityPolicy;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.goals.domain.GoalContribution;
import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.GoalStatus;
import com.familion.finance.goals.domain.SavingsGoal;
import com.familion.finance.goals.domain.SavingsGoalCalculator;
import com.familion.finance.goals.infrastructure.persistence.GoalContributionRepository;
import com.familion.finance.goals.infrastructure.persistence.SavingsGoalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavingsGoalService {

    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_CURRENCY_LENGTH = 10;
    private static final int MAX_NOTES_LENGTH = 255;

    private final SavingsGoalRepository savingsGoalRepository;
    private final GoalContributionRepository goalContributionRepository;
    private final SavingsGoalReadQuery readQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Clock clock;
    private final SavingsGoalCalculator calculator;
    private final SavingsGoalCalculator.RateProvider rateProvider;

    public SavingsGoalService(
            SavingsGoalRepository savingsGoalRepository,
            GoalContributionRepository goalContributionRepository,
            SavingsGoalReadQuery readQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.goalContributionRepository = goalContributionRepository;
        this.readQuery = readQuery;
        this.visibilityPolicy = visibilityPolicy;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.clock = clock;
        this.calculator = new SavingsGoalCalculator();
        this.rateProvider = new EconomicsRateProvider(economicsApi);
    }

    @Transactional(readOnly = true)
    public List<SavingsGoalView> list(
            CurrentUser currentUser, View requestedView, GoalStatus statusFilter) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");

        View view = requestedView != null ? requestedView : View.PERSONAL;
        GoalListingScope listingScope = resolveListingScope(currentUser, view);

        List<SavingsGoalReadRow> rows = readQuery.findGoals(
                currentUser.familyId(), currentUser.userId(), listingScope, statusFilter);
        if (rows.isEmpty()) {
            return List.of();
        }

        FamilySettings settings = familyQueryApi.getSettings(currentUser.familyId());
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));

        return rows.stream().map(row -> toGoalView(row, settings, today)).toList();
    }

    @Transactional
    public SavingsGoalView create(CurrentUser currentUser, CreateSavingsGoalCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(command, "command must not be null");

        String trimmedName = requireValidName(command.name());
        GoalScope scope = Objects.requireNonNull(command.scope(), "scope must not be null");
        if (command.targetUnit() == null) {
            throw new BusinessRuleException("Target unit is required");
        }
        BigDecimal targetAmount = requirePositiveAmount(command.targetAmount(), "Target amount");

        UUID familyId = currentUser.familyId();
        UUID ownerUserId;
        SavingsGoalView.UserSummary ownerSummary;
        if (scope == GoalScope.FAMILY) {
            if (!currentUser.isAdult()) {
                throw new ForbiddenException("Only adults can create family savings goals");
            }
            ownerUserId = null;
            ownerSummary = null;
        } else {
            ownerUserId = currentUser.userId();
            MemberRef member = familyQueryApi.requireMember(familyId, ownerUserId);
            ownerSummary = new SavingsGoalView.UserSummary(member.userId(), member.fullName());
        }

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        Instant now = Instant.now(clock);
        SavingsGoal goal = SavingsGoal.create(
                familyId,
                ownerUserId,
                scope,
                trimmedName,
                targetAmount,
                command.targetUnit(),
                command.targetDate(),
                now);
        SavingsGoal saved = savingsGoalRepository.saveAndFlush(goal);

        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        LocalDate createdDate = saved.getCreatedAt().atZone(settings.timezone()).toLocalDate();
        SavingsGoalCalculator.Progress progress = calculator.computeProgress(
                saved.getTargetAmount(), BigDecimal.ZERO, saved.getTargetDate(), today);
        BigDecimal accumulatedInBaseToday = calculator.computeAccumulatedInBaseToday(
                progress.accumulatedInUnit(),
                saved.getTargetUnit(),
                createdDate,
                today,
                settings.baseCurrency(),
                settings.referenceRateSource(),
                rateProvider);

        return new SavingsGoalView(
                saved.getId(),
                saved.getName(),
                saved.getScope(),
                ownerSummary,
                saved.getTargetAmount(),
                saved.getTargetUnit(),
                saved.getTargetDate(),
                saved.getStatus(),
                progress.accumulatedInUnit(),
                progress.progressPct(),
                progress.remainingInUnit(),
                progress.monthsLeft(),
                progress.suggestedMonthlyInUnit(),
                0,
                null,
                accumulatedInBaseToday);
    }

    @Transactional
    public SavingsGoalView patch(
            CurrentUser currentUser, UUID goalId, PatchSavingsGoalCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        UUID familyId = currentUser.familyId();
        SavingsGoal goal = savingsGoalRepository
                .findByIdAndFamilyId(goalId, familyId)
                .orElseThrow(() -> new NotFoundException("Savings goal not found"));

        if (!currentUser.isAdult() && !goal.isOwnedBy(currentUser.userId())) {
            throw new ForbiddenException("You do not have permission to modify this savings goal");
        }

        String effectiveName =
                command.name() != null ? requireValidName(command.name()) : goal.getName();
        BigDecimal effectiveTargetAmount = command.targetAmount() != null
                ? requirePositiveAmount(command.targetAmount(), "Target amount")
                : goal.getTargetAmount();
        LocalDate effectiveTargetDate =
                command.targetDatePresent() ? command.targetDate() : goal.getTargetDate();
        GoalStatus effectiveStatus =
                command.status() != null ? command.status() : goal.getStatus();

        if (command.status() == null && command.targetAmount() != null) {
            BigDecimal accumulated = Money.scale(
                    goalContributionRepository.sumAmountInGoalUnitBySavingsGoalId(goalId));
            if (effectiveStatus == GoalStatus.ACTIVE
                    && accumulated.compareTo(effectiveTargetAmount) >= 0) {
                effectiveStatus = GoalStatus.COMPLETED;
            } else if (effectiveStatus == GoalStatus.COMPLETED
                    && accumulated.compareTo(effectiveTargetAmount) < 0) {
                effectiveStatus = GoalStatus.ACTIVE;
            }
        }

        goal.update(effectiveName, effectiveTargetAmount, effectiveTargetDate, effectiveStatus);
        savingsGoalRepository.saveAndFlush(goal);

        SavingsGoalReadRow updatedRow = readQuery
                .findGoalById(familyId, goalId)
                .orElseThrow(() -> new NotFoundException("Savings goal not found"));
        FamilySettings settings = familyQueryApi.getSettings(familyId);
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        return toGoalView(updatedRow, settings, today);
    }

    @Transactional(readOnly = true)
    public List<ContributionView> listContributions(CurrentUser currentUser, UUID goalId) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");

        SavingsGoal goal = savingsGoalRepository
                .findByIdAndFamilyId(goalId, currentUser.familyId())
                .orElseThrow(() -> new NotFoundException("Savings goal not found"));
        requireCanAccessGoal(currentUser, goal);

        return readQuery.findContributionsByGoalId(goalId).stream()
                .map(row -> new ContributionView(
                        row.id(),
                        new ContributionView.UserSummary(row.userId(), row.userFullName()),
                        row.amount(),
                        row.currencyCode(),
                        row.amountInGoalUnit(),
                        row.contributionDate(),
                        row.notes(),
                        row.transactionId()))
                .toList();
    }

    @Transactional
    public ContributionView addContribution(
            CurrentUser currentUser, UUID goalId, CreateContributionCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        UUID familyId = currentUser.familyId();
        SavingsGoal goal = savingsGoalRepository
                .findByIdAndFamilyId(goalId, familyId)
                .orElseThrow(() -> new NotFoundException("Savings goal not found"));
        requireCanAccessGoal(currentUser, goal);

        if (goal.getStatus() == GoalStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Cannot add contributions to a cancelled savings goal");
        }

        BigDecimal amount = requirePositiveAmount(command.amount(), "Contribution amount");
        String currencyCode = requireValidCurrency(command.currencyCode());
        String notes = normalizeNotes(command.notes());

        if (command.transactionId() != null
                && !readQuery.transactionExistsInFamily(familyId, command.transactionId())) {
            throw new NotFoundException("Transaction not found");
        }

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        ZoneId zone = settings.timezone();
        LocalDate contributionDate = command.contributionDate() != null
                ? command.contributionDate()
                : LocalDate.now(clock.withZone(zone));
        LocalDate goalCreatedDate = goal.getCreatedAt().atZone(zone).toLocalDate();

        BigDecimal amountInGoalUnit = calculator.convertContributionToGoalUnit(
                amount,
                currencyCode,
                goal.getTargetUnit(),
                contributionDate,
                goalCreatedDate,
                settings.baseCurrency(),
                settings.referenceRateSource(),
                rateProvider);

        MemberRef author = familyQueryApi.requireMember(familyId, currentUser.userId());
        Instant now = Instant.now(clock);
        GoalContribution contribution = GoalContribution.create(
                goalId,
                currentUser.userId(),
                command.transactionId(),
                amount,
                currencyCode,
                amountInGoalUnit,
                contributionDate,
                notes,
                now);
        GoalContribution saved = goalContributionRepository.saveAndFlush(contribution);

        BigDecimal totalAccumulated = Money.scale(
                goalContributionRepository.sumAmountInGoalUnitBySavingsGoalId(goalId));
        if (goal.getStatus() != GoalStatus.COMPLETED
                && totalAccumulated.compareTo(goal.getTargetAmount()) >= 0) {
            goal.changeStatus(GoalStatus.COMPLETED);
            savingsGoalRepository.saveAndFlush(goal);
        }

        return new ContributionView(
                saved.getId(),
                new ContributionView.UserSummary(author.userId(), author.fullName()),
                saved.getAmount(),
                saved.getCurrencyCode(),
                saved.getAmountInGoalUnit(),
                saved.getContributionDate(),
                saved.getNotes(),
                saved.getTransactionId());
    }

    @Transactional
    public void deleteContribution(CurrentUser currentUser, UUID goalId, UUID contributionId) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(contributionId, "contributionId must not be null");

        UUID familyId = currentUser.familyId();
        SavingsGoal goal = savingsGoalRepository
                .findByIdAndFamilyId(goalId, familyId)
                .orElseThrow(() -> new NotFoundException("Savings goal not found"));

        GoalContribution contribution = goalContributionRepository
                .findByIdAndSavingsGoalId(contributionId, goalId)
                .orElseThrow(() -> new NotFoundException("Contribution not found"));

        if (!currentUser.isAdult() && !contribution.isAuthoredBy(currentUser.userId())) {
            throw new ForbiddenException("You do not have permission to delete this contribution");
        }

        goalContributionRepository.delete(contribution);
        goalContributionRepository.flush();

        BigDecimal totalAccumulated = Money.scale(
                goalContributionRepository.sumAmountInGoalUnitBySavingsGoalId(goalId));
        if (goal.getStatus() == GoalStatus.COMPLETED
                && totalAccumulated.compareTo(goal.getTargetAmount()) < 0) {
            goal.changeStatus(GoalStatus.ACTIVE);
            savingsGoalRepository.saveAndFlush(goal);
        }
    }

    private GoalListingScope resolveListingScope(CurrentUser currentUser, View view) {
        if (view == View.PERSONAL) {
            visibilityPolicy.resolveForListing(currentUser, View.PERSONAL);
            return GoalListingScope.OWN_PERSONAL;
        }
        if (currentUser.isAdult()) {
            visibilityPolicy.resolveForListing(currentUser, View.FAMILY);
            return GoalListingScope.ALL_FAMILY;
        }
        if (currentUser.visibility() == FamilyVisibility.OWN_ONLY) {
            throw new ForbiddenException("User is not allowed to view family savings goals");
        }
        visibilityPolicy.resolveForTotals(currentUser, View.FAMILY);
        return GoalListingScope.FAMILY_SCOPE_ONLY;
    }

    private static void requireCanAccessGoal(CurrentUser currentUser, SavingsGoal goal) {
        if (currentUser.isAdult()) {
            return;
        }
        if (goal.getScope() == GoalScope.PERSONAL) {
            if (!goal.isOwnedBy(currentUser.userId())) {
                throw new ForbiddenException("You do not have permission to access this savings goal");
            }
            return;
        }
        if (currentUser.visibility() == FamilyVisibility.OWN_ONLY) {
            throw new ForbiddenException("User is not allowed to access family savings goals");
        }
    }

    private SavingsGoalView toGoalView(
            SavingsGoalReadRow row, FamilySettings settings, LocalDate today) {
        LocalDate createdDate = row.createdAt().atZone(settings.timezone()).toLocalDate();
        SavingsGoalCalculator.Progress progress = calculator.computeProgress(
                row.targetAmount(), row.accumulatedInUnit(), row.targetDate(), today);
        BigDecimal accumulatedInBaseToday = calculator.computeAccumulatedInBaseToday(
                progress.accumulatedInUnit(),
                row.targetUnit(),
                createdDate,
                today,
                settings.baseCurrency(),
                settings.referenceRateSource(),
                rateProvider);

        SavingsGoalView.UserSummary owner = row.ownerUserId() == null
                ? null
                : new SavingsGoalView.UserSummary(row.ownerUserId(), row.ownerFullName());

        return new SavingsGoalView(
                row.id(),
                row.name(),
                row.scope(),
                owner,
                row.targetAmount(),
                row.targetUnit(),
                row.targetDate(),
                row.status(),
                progress.accumulatedInUnit(),
                progress.progressPct(),
                progress.remainingInUnit(),
                progress.monthsLeft(),
                progress.suggestedMonthlyInUnit(),
                row.contributionsCount(),
                row.lastContributionDate(),
                accumulatedInBaseToday);
    }

    private static String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Savings goal name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException(
                    "Savings goal name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount, String label) {
        if (amount == null) {
            throw new BusinessRuleException(label + " is required");
        }
        BigDecimal scaled = Money.scale(amount);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(label + " must be greater than zero");
        }
        return scaled;
    }

    private static String requireValidCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new BusinessRuleException("Currency code is required");
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_CURRENCY_LENGTH) {
            throw new BusinessRuleException(
                    "Currency code must be at most " + MAX_CURRENCY_LENGTH + " characters");
        }
        return normalized;
    }

    private static String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_NOTES_LENGTH) {
            throw new BusinessRuleException(
                    "Notes must be at most " + MAX_NOTES_LENGTH + " characters");
        }
        return trimmed;
    }

    private record EconomicsRateProvider(EconomicsApi economicsApi)
            implements SavingsGoalCalculator.RateProvider {

        @Override
        public BigDecimal convert(
                BigDecimal amount,
                String fromCurrency,
                String toCurrency,
                LocalDate date,
                String rateSource) {
            return economicsApi.convert(amount, fromCurrency, toCurrency, date, rateSource);
        }

        @Override
        public Optional<BigDecimal> valueAt(String indexCode, LocalDate date) {
            return economicsApi.valueAt(indexCode, date);
        }

        @Override
        public Optional<BigDecimal> latestValue(String indexCode) {
            return economicsApi.latestValue(indexCode);
        }
    }
}
