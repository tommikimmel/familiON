package com.familion.finance.goals.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.DefaultVisibilityPolicy;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.security.View;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.goals.domain.GoalContribution;
import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.GoalStatus;
import com.familion.finance.goals.domain.SavingsGoal;
import com.familion.finance.goals.domain.TargetUnit;
import com.familion.finance.goals.infrastructure.persistence.GoalContributionRepository;
import com.familion.finance.goals.infrastructure.persistence.SavingsGoalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsGoalServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T15:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private GoalContributionRepository goalContributionRepository;

    @Mock
    private SavingsGoalReadQuery readQuery;

    @Mock
    private FamilyQueryApi familyQueryApi;

    @Mock
    private EconomicsApi economicsApi;

    private SavingsGoalService service;
    private UUID familyId;
    private UUID adultId;
    private UUID childId;
    private CurrentUser adult;
    private CurrentUser childSummary;
    private CurrentUser childOwnOnly;
    private FamilySettings settings;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new SavingsGoalService(
                savingsGoalRepository,
                goalContributionRepository,
                readQuery,
                new DefaultVisibilityPolicy(),
                familyQueryApi,
                economicsApi,
                clock);
        familyId = UUID.randomUUID();
        adultId = UUID.randomUUID();
        childId = UUID.randomUUID();
        adult = new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childSummary = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        childOwnOnly = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        settings = new FamilySettings(
                familyId, "Test Family", "ARS", "USD", "USD_MEP", ZONE, List.of("ARS", "USD"));
    }

    @Test
    void childCannotCreateFamilyGoal() {
        CreateSavingsGoalCommand cmd = new CreateSavingsGoalCommand(
                "Vacaciones", GoalScope.FAMILY, new BigDecimal("2000"), TargetUnit.USD, null);

        assertThatThrownBy(() -> service.create(childSummary, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listResolvesScopesAccordingToRoleAndVisibility() {
        when(readQuery.findGoals(eq(familyId), any(), any(), any())).thenReturn(List.of());

        service.list(adult, View.PERSONAL, null);
        verify(readQuery).findGoals(familyId, adultId, GoalListingScope.OWN_PERSONAL, null);

        service.list(adult, View.FAMILY, GoalStatus.ACTIVE);
        verify(readQuery).findGoals(familyId, adultId, GoalListingScope.ALL_FAMILY, GoalStatus.ACTIVE);

        service.list(childSummary, View.FAMILY, null);
        verify(readQuery).findGoals(familyId, childId, GoalListingScope.FAMILY_SCOPE_ONLY, null);

        assertThatThrownBy(() -> service.list(childOwnOnly, View.FAMILY, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void childCannotPatchAnotherMembersOrFamilyGoal() {
        UUID goalId = UUID.randomUUID();
        SavingsGoal familyGoal = SavingsGoal.create(
                familyId,
                null,
                GoalScope.FAMILY,
                "Auto",
                new BigDecimal("10000"),
                TargetUnit.USD,
                null,
                NOW);
        when(savingsGoalRepository.findByIdAndFamilyId(goalId, familyId))
                .thenReturn(Optional.of(familyGoal));

        PatchSavingsGoalCommand patch =
                new PatchSavingsGoalCommand("Auto nuevo", null, false, null, null);
        assertThatThrownBy(() -> service.patch(childSummary, goalId, patch))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addContributionRejectsCancelledGoalAndUnknownTransaction() {
        UUID goalId = UUID.randomUUID();
        SavingsGoal cancelled = SavingsGoal.create(
                familyId,
                null,
                GoalScope.FAMILY,
                "Cancelada",
                new BigDecimal("1000"),
                TargetUnit.USD,
                null,
                NOW);
        cancelled.changeStatus(GoalStatus.CANCELLED);
        when(savingsGoalRepository.findByIdAndFamilyId(goalId, familyId))
                .thenReturn(Optional.of(cancelled));

        CreateContributionCommand cmd = new CreateContributionCommand(
                new BigDecimal("100"), "USD", LocalDate.of(2026, 6, 15), null, null);
        assertThatThrownBy(() -> service.addContribution(adult, goalId, cmd))
                .isInstanceOf(BusinessRuleException.class);

        SavingsGoal active = SavingsGoal.create(
                familyId,
                null,
                GoalScope.FAMILY,
                "Activa",
                new BigDecimal("1000"),
                TargetUnit.USD,
                null,
                NOW);
        when(savingsGoalRepository.findByIdAndFamilyId(goalId, familyId))
                .thenReturn(Optional.of(active));
        UUID missingTxId = UUID.randomUUID();
        when(readQuery.transactionExistsInFamily(familyId, missingTxId)).thenReturn(false);

        CreateContributionCommand withTx = new CreateContributionCommand(
                new BigDecimal("100"), "USD", LocalDate.of(2026, 6, 15), null, missingTxId);
        assertThatThrownBy(() -> service.addContribution(adult, goalId, withTx))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addContributionAutoCompletesGoalAtHundredPercent() {
        UUID goalId = UUID.randomUUID();
        SavingsGoal goal = SavingsGoal.create(
                familyId,
                null,
                GoalScope.FAMILY,
                "Fondo",
                new BigDecimal("500"),
                TargetUnit.USD,
                null,
                NOW);
        when(savingsGoalRepository.findByIdAndFamilyId(goalId, familyId))
                .thenReturn(Optional.of(goal));
        when(familyQueryApi.getSettings(familyId)).thenReturn(settings);
        when(familyQueryApi.requireMember(familyId, adultId))
                .thenReturn(new MemberRef(adultId, familyId, "Ana", Role.ADULTO, "#000000"));
        when(economicsApi.convert(
                        new BigDecimal("500.0000"), "USD", "USD", LocalDate.of(2026, 6, 15), "USD_MEP"))
                .thenReturn(new BigDecimal("500.0000"));
        when(goalContributionRepository.saveAndFlush(any(GoalContribution.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(goalContributionRepository.sumAmountInGoalUnitBySavingsGoalId(goalId))
                .thenReturn(new BigDecimal("500.0000"));

        ContributionView view = service.addContribution(
                adult,
                goalId,
                new CreateContributionCommand(
                        new BigDecimal("500"), "USD", LocalDate.of(2026, 6, 15), "Meta!", null));

        assertThat(view.amountInGoalUnit()).isEqualByComparingTo("500.0000");
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.COMPLETED);
        verify(savingsGoalRepository).saveAndFlush(goal);
    }

    @Test
    void deleteContributionRejectsNonAuthorChildAndRevertsCompletedStatus() {
        UUID goalId = UUID.randomUUID();
        UUID contribId = UUID.randomUUID();
        SavingsGoal goal = SavingsGoal.create(
                familyId,
                null,
                GoalScope.FAMILY,
                "Fondo",
                new BigDecimal("500"),
                TargetUnit.USD,
                null,
                NOW);
        goal.changeStatus(GoalStatus.COMPLETED);
        GoalContribution adultContribution = GoalContribution.create(
                goalId,
                adultId,
                null,
                new BigDecimal("500"),
                "USD",
                new BigDecimal("500"),
                LocalDate.of(2026, 6, 15),
                null,
                NOW);

        when(savingsGoalRepository.findByIdAndFamilyId(goalId, familyId))
                .thenReturn(Optional.of(goal));
        when(goalContributionRepository.findByIdAndSavingsGoalId(contribId, goalId))
                .thenReturn(Optional.of(adultContribution));

        assertThatThrownBy(() -> service.deleteContribution(childSummary, goalId, contribId))
                .isInstanceOf(ForbiddenException.class);

        when(goalContributionRepository.sumAmountInGoalUnitBySavingsGoalId(goalId))
                .thenReturn(new BigDecimal("200.0000"));

        service.deleteContribution(adult, goalId, contribId);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        verify(goalContributionRepository).delete(adultContribution);
        verify(savingsGoalRepository).saveAndFlush(goal);
    }
}
