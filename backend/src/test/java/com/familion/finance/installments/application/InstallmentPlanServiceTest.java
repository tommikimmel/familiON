package com.familion.finance.installments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.installments.domain.AmountMode;
import com.familion.finance.installments.domain.CancelRemainingMode;
import com.familion.finance.installments.domain.InstallmentPlan;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import com.familion.finance.installments.infrastructure.persistence.InstallmentPlanRepository;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InstallmentPlanServiceTest {

    // 2026-05-10 12:00 UTC -> 2026-05-10 09:00 Argentina
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private InstallmentPlanRepository planRepository;

    @Mock
    private InstallmentPlanReadQuery readQuery;

    @Mock
    private TransactionCommandApi transactionCommandApi;

    @Mock
    private AccountQueryApi accountQueryApi;

    @Mock
    private CategoryQueryApi categoryQueryApi;

    @Mock
    private FamilyQueryApi familyQueryApi;

    @Mock
    private EconomicsApi economicsApi;

    private InstallmentPlanService service;

    private UUID familyId;
    private UUID adultId;
    private UUID childId;
    private UUID cardId;
    private UUID categoryId;
    private CurrentUser adult;
    private CurrentUser child;

    @BeforeEach
    void setUp() {
        service = new InstallmentPlanService(
                planRepository,
                readQuery,
                transactionCommandApi,
                accountQueryApi,
                categoryQueryApi,
                familyQueryApi,
                economicsApi,
                FIXED_CLOCK);

        familyId = UUID.randomUUID();
        adultId = UUID.randomUUID();
        childId = UUID.randomUUID();
        cardId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        adult = new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        child = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
    }

    @Test
    void createProjectsInstallmentsWithPostedAndProjectedStatusesAndExactSum() {
        AccountRef card = new AccountRef(
                cardId, familyId, adultId, "Visa Gold", "CREDIT_CARD", "ARS", 25, 5);
        FamilySettings settings = new FamilySettings(
                familyId,
                "Family",
                "ARS",
                "USD",
                "USD_MEP",
                ZoneId.of("America/Argentina/Buenos_Aires"),
                List.of("ARS", "USD"));
        MemberRef attributed =
                new MemberRef(childId, familyId, "Child Member", Role.HIJO, "#00AA88");
        CategoryRef category =
                new CategoryRef(categoryId, "Electro", "EXPENSE", "tv", "#112233");

        when(accountQueryApi.requireUsable(adult, cardId)).thenReturn(card);
        when(familyQueryApi.getSettings(familyId)).thenReturn(settings);
        when(familyQueryApi.requireMember(familyId, childId)).thenReturn(attributed);
        when(categoryQueryApi.requireUsable(familyId, categoryId, "EXPENSE"))
                .thenReturn(category);
        when(economicsApi.latestValue("IPC_MENSUAL")).thenReturn(Optional.of(new BigDecimal("10")));

        UUID planId = UUID.randomUUID();
        when(planRepository.saveAndFlush(any(InstallmentPlan.class))).thenAnswer(inv -> {
            InstallmentPlan p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", planId);
            return p;
        });
        when(transactionCommandApi.record(any(NewTransactionCommand.class)))
                .thenAnswer(inv -> UUID.randomUUID());

        // Purchase 2026-03-20 (before closing 25) -> 1st 2026-04-05 (POSTED),
        // 2nd 2026-05-05 (POSTED), 3rd 2026-06-05 (PROJECTED) on today=2026-05-10
        InstallmentPlanView view = service.create(
                adult,
                new CreateInstallmentPlanCommand(
                        "Smart TV",
                        categoryId,
                        cardId,
                        "ARS",
                        3,
                        AmountMode.TOTAL,
                        new BigDecimal("100.00"),
                        false,
                        new BigDecimal("80.00"),
                        LocalDate.of(2026, 3, 20),
                        childId));

        assertThat(view.id()).isEqualTo(planId);
        assertThat(view.cashPrice()).isNull(); // ignored when hasInterest=false
        assertThat(view.surchargeAmount()).isNull();
        assertThat(view.surchargePct()).isNull();
        assertThat(view.paidInstallments()).isEqualTo(2);
        assertThat(view.remainingAmount()).isEqualByComparingTo("33.3334");
        // k=1 (2026-05 -> 2026-06), 33.3334 / 1.10 = 30.3031
        assertThat(view.realRemainingAmount()).isEqualByComparingTo("30.3031");
        assertThat(view.nextInstallment().number()).isEqualTo(3);
        assertThat(view.nextInstallment().date()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(view.nextInstallment().amount()).isEqualByComparingTo("33.3334");
        assertThat(view.status()).isEqualTo(InstallmentPlanStatus.ACTIVE);
        assertThat(view.attributedTo().id()).isEqualTo(childId);

        ArgumentCaptor<NewTransactionCommand> captor =
                ArgumentCaptor.forClass(NewTransactionCommand.class);
        verify(transactionCommandApi, times(3)).record(captor.capture());

        List<NewTransactionCommand> recorded = captor.getAllValues();
        assertThat(recorded)
                .extracting(NewTransactionCommand::status)
                .containsExactly(
                        TransactionStatus.POSTED,
                        TransactionStatus.POSTED,
                        TransactionStatus.PROJECTED);
        assertThat(recorded)
                .extracting(NewTransactionCommand::amount)
                .containsExactly(
                        new BigDecimal("33.3333"),
                        new BigDecimal("33.3333"),
                        new BigDecimal("33.3334"));
        assertThat(recorded)
                .allSatisfy(cmd -> {
                    assertThat(cmd.type()).isEqualTo(TransactionType.EXPENSE);
                    assertThat(cmd.scope()).isEqualTo(Scope.PERSONAL);
                    assertThat(cmd.installmentPlanId()).isEqualTo(planId);
                    assertThat(cmd.attributedToUserId()).isEqualTo(childId);
                });
    }

    @Test
    void rejectsNonCreditCardAccountWithBusinessRuleException() {
        AccountRef bank = new AccountRef(
                cardId, familyId, adultId, "Checking", "BANK", "ARS", null, null);
        when(accountQueryApi.requireUsable(adult, cardId)).thenReturn(bank);

        assertThatThrownBy(() -> service.create(
                        adult,
                        new CreateInstallmentPlanCommand(
                                "Laptop",
                                categoryId,
                                cardId,
                                "ARS",
                                6,
                                AmountMode.TOTAL,
                                new BigDecimal("600000"),
                                false,
                                null,
                                LocalDate.of(2026, 5, 1),
                                null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CREDIT_CARD");
    }

    @Test
    void rejectsChildAttributingPlanToAnotherMemberWithForbiddenException() {
        assertThatThrownBy(() -> service.create(
                        child,
                        new CreateInstallmentPlanCommand(
                                "Headphones",
                                categoryId,
                                cardId,
                                "ARS",
                                3,
                                AmountMode.TOTAL,
                                new BigDecimal("30000"),
                                false,
                                null,
                                LocalDate.of(2026, 5, 1),
                                adultId)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancelRemainingRejectsClosedPlanWithConflictException() {
        UUID planId = UUID.randomUUID();
        InstallmentPlan closedPlan = InstallmentPlan.create(
                familyId,
                adultId,
                cardId,
                categoryId,
                "Desk",
                "ARS",
                3,
                false,
                null,
                new BigDecimal("30000"),
                new BigDecimal("10000"),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 5),
                InstallmentPlanStatus.COMPLETED);
        ReflectionTestUtils.setField(closedPlan, "id", planId);

        when(planRepository.findByIdAndFamilyId(planId, familyId))
                .thenReturn(Optional.of(closedPlan));

        assertThatThrownBy(() -> service.cancelRemaining(
                        adult, planId, new CancelRemainingCommand(CancelRemainingMode.CANCEL, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelRemainingRejectsNonAuthorChildWithForbiddenException() {
        UUID planId = UUID.randomUUID();
        InstallmentPlan adultPlan = InstallmentPlan.create(
                familyId,
                adultId,
                cardId,
                categoryId,
                "Desk",
                "ARS",
                3,
                false,
                null,
                new BigDecimal("30000"),
                new BigDecimal("10000"),
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 5, 5),
                InstallmentPlanStatus.ACTIVE);
        ReflectionTestUtils.setField(adultPlan, "id", planId);

        when(planRepository.findByIdAndFamilyId(planId, familyId))
                .thenReturn(Optional.of(adultPlan));

        assertThatThrownBy(() -> service.cancelRemaining(
                        child, planId, new CancelRemainingCommand(CancelRemainingMode.CANCEL, null)))
                .isInstanceOf(ForbiddenException.class);
    }
}
