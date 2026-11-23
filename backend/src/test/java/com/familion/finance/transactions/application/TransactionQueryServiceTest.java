package com.familion.finance.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.DefaultVisibilityPolicy;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.security.View;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Lens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class TransactionQueryServiceTest {

    private TransactionReadQuery readQuery;
    private FamilyQueryApi familyQueryApi;
    private EconomicsApi economicsApi;
    private TransactionQueryService service;

    private final UUID familyId = UUID.randomUUID();
    private final UUID adultId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        readQuery = mock(TransactionReadQuery.class);
        familyQueryApi = mock(FamilyQueryApi.class);
        economicsApi = mock(EconomicsApi.class);
        service = new TransactionQueryService(
                readQuery, new DefaultVisibilityPolicy(), familyQueryApi, economicsApi);
    }

    @Test
    void clampsPageSizeAbove50To50AndSkipsAuxiliaryQueriesWhenEmpty() {
        CurrentUser adult =
                new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        TransactionFilter filter = new TransactionFilter(
                View.FAMILY, Lens.INCOME_PCT, null, null, null, null, null, null, null, null, null);
        when(readQuery.findPage(eq(familyId), isNull(), eq(filter), any())).thenReturn(List.of());
        when(readQuery.count(eq(familyId), isNull(), eq(filter))).thenReturn(0L);

        Page<TransactionItemView> result = service.list(adult, filter, PageRequest.of(0, 51));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(readQuery).findPage(eq(familyId), isNull(), eq(filter), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(economicsApi, familyQueryApi);
    }

    @Test
    void rejectsFamilyListingForOwnOnlyAndSummaryChildren() {
        CurrentUser ownOnly =
                new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        CurrentUser summary =
                new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        TransactionFilter filter = new TransactionFilter(
                View.FAMILY, Lens.NOMINAL, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.list(ownOnly, filter, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.list(summary, filter, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void appliesConstantBaseAndIncomePctLensesInSingleBatch() {
        CurrentUser adult =
                new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        TransactionReadRow row = new TransactionReadRow(
                UUID.randomUUID(),
                TransactionType.EXPENSE,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                new BigDecimal("10000.0000"),
                "ARS",
                LocalDate.of(2026, 10, 15),
                "Groceries",
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                "Bank",
                "BANK",
                adultId,
                "Adult",
                adultId,
                "Adult",
                null,
                null,
                null,
                "USD",
                new BigDecimal("10.0000"),
                new BigDecimal("10000.0000"),
                new BigDecimal("200.000000"));

        FamilySettings settings = new FamilySettings(
                familyId,
                "Fam",
                "ARS",
                "USD",
                "USD_MEP",
                ZoneId.of("UTC"),
                List.of("ARS", "USD"));
        when(familyQueryApi.getSettings(familyId)).thenReturn(settings);

        TransactionFilter constantFilter = new TransactionFilter(
                View.PERSONAL,
                Lens.CONSTANT_BASE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(readQuery.findPage(eq(familyId), eq(adultId), eq(constantFilter), any()))
                .thenReturn(List.of(row));
        when(readQuery.count(eq(familyId), eq(adultId), eq(constantFilter))).thenReturn(1L);
        when(economicsApi.latestValue("IPC_ACUMULADO"))
                .thenReturn(Optional.of(new BigDecimal("250.000000")));

        Page<TransactionItemView> constantPage =
                service.list(adult, constantFilter, PageRequest.of(0, 20));
        assertThat(constantPage.getContent().getFirst().lensAmount())
                .isEqualByComparingTo("12500.0000");
        assertThat(constantPage.getContent().getFirst().lensApproximated()).isFalse();

        TransactionFilter incomePctFilter = new TransactionFilter(
                View.PERSONAL,
                Lens.INCOME_PCT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(readQuery.findPage(eq(familyId), eq(adultId), eq(incomePctFilter), any()))
                .thenReturn(List.of(row));
        when(readQuery.count(eq(familyId), eq(adultId), eq(incomePctFilter))).thenReturn(1L);
        when(readQuery.sumPostedIncomeByMonth(
                        familyId, adultId, Set.of(YearMonth.of(2026, 10))))
                .thenReturn(Map.of(YearMonth.of(2026, 10), new BigDecimal("50000.0000")));

        Page<TransactionItemView> pctPage =
                service.list(adult, incomePctFilter, PageRequest.of(0, 20));
        assertThat(pctPage.getContent().getFirst().lensAmount()).isEqualByComparingTo("20.0000");
        assertThat(pctPage.getContent().getFirst().lensUnit()).isEqualTo("%");
    }
}
