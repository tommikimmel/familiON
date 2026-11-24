package com.familion.finance.exchanges.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.exchanges.domain.CurrencyExchange;
import com.familion.finance.exchanges.domain.ExchangeOperation;
import com.familion.finance.exchanges.infrastructure.persistence.CurrencyExchangeRepository;
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
class ExchangeServiceTest {

    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final UUID ADULT_ID = UUID.randomUUID();
    private static final UUID CHILD_ID = UUID.randomUUID();
    private static final UUID ARS_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID USD_ACCOUNT_ID = UUID.randomUUID();

    private static final CurrentUser ADULT =
            new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    private static final CurrentUser CHILD =
            new CurrentUser(CHILD_ID, FAMILY_ID, Role.HIJO, FamilyVisibility.OWN_ONLY);

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-24T01:30:00Z"), ZoneOffset.UTC);

    @Mock
    private CurrencyExchangeRepository exchangeRepository;

    @Mock
    private ExchangeReadQuery exchangeReadQuery;

    @Mock
    private TransactionCommandApi transactionCommandApi;

    @Mock
    private AccountQueryApi accountQueryApi;

    @Mock
    private CategoryQueryApi categoryQueryApi;

    @Mock
    private FamilyQueryApi familyQueryApi;

    private ExchangeService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeService(
                exchangeRepository,
                exchangeReadQuery,
                transactionCommandApi,
                accountQueryApi,
                categoryQueryApi,
                familyQueryApi,
                FIXED_CLOCK);
    }

    @Test
    void createBuyRecordsExchangeAndMirroredTransactionsWithFamilyTimezoneDefaultDate() {
        AccountRef arsAccount = account(ARS_ACCOUNT_ID, "Banco ARS", "ARS");
        AccountRef usdAccount = account(USD_ACCOUNT_ID, "Caja USD", "USD");
        UUID savedExchangeId = UUID.randomUUID();

        when(accountQueryApi.requireUsable(ADULT, ARS_ACCOUNT_ID)).thenReturn(arsAccount);
        when(accountQueryApi.requireUsable(ADULT, USD_ACCOUNT_ID)).thenReturn(usdAccount);
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(defaultSettings());
        when(familyQueryApi.requireMember(FAMILY_ID, ADULT_ID))
                .thenReturn(new MemberRef(ADULT_ID, FAMILY_ID, "Ana Adult", Role.ADULTO, "#000000"));
        when(exchangeRepository.saveAndFlush(any(CurrencyExchange.class))).thenAnswer(inv -> {
            CurrencyExchange ex = inv.getArgument(0);
            ReflectionTestUtils.setField(ex, "id", savedExchangeId);
            return ex;
        });

        ExchangeView view = service.create(
                ADULT,
                new CreateExchangeCommand(
                        ARS_ACCOUNT_ID,
                        USD_ACCOUNT_ID,
                        new BigDecimal("1185000"),
                        new BigDecimal("1000"),
                        null,
                        null,
                        null,
                        null,
                        "  MEP purchase  "));

        assertThat(view.id()).isEqualTo(savedExchangeId);
        // 2026-11-24T01:30:00Z is 2026-11-23 22:30 in America/Argentina/Buenos_Aires
        assertThat(view.exchangeDate()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(view.operation()).isEqualTo(ExchangeOperation.BUY);
        assertThat(view.appliedExchangeRate()).isEqualByComparingTo("1185.000000");
        assertThat(view.feeAmount()).isEqualByComparingTo("0.0000");
        assertThat(view.feeCurrency()).isNull();
        assertThat(view.notes()).isEqualTo("MEP purchase");
        assertThat(view.createdBy().fullName()).isEqualTo("Ana Adult");

        ArgumentCaptor<NewTransactionCommand> cmdCaptor =
                ArgumentCaptor.forClass(NewTransactionCommand.class);
        verify(transactionCommandApi, times(2)).record(cmdCaptor.capture());

        NewTransactionCommand outCmd = cmdCaptor.getAllValues().get(0);
        NewTransactionCommand inCmd = cmdCaptor.getAllValues().get(1);

        assertThat(outCmd.type()).isEqualTo(TransactionType.EXCHANGE_OUT);
        assertThat(outCmd.accountId()).isEqualTo(ARS_ACCOUNT_ID);
        assertThat(outCmd.amount()).isEqualByComparingTo("1185000.0000");
        assertThat(outCmd.currencyCode()).isEqualTo("ARS");
        assertThat(outCmd.categoryId()).isNull();
        assertThat(outCmd.scope()).isEqualTo(Scope.PERSONAL);
        assertThat(outCmd.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(outCmd.attributedToUserId()).isEqualTo(ADULT_ID);
        assertThat(outCmd.currencyExchangeId()).isEqualTo(savedExchangeId);
        assertThat(outCmd.transferGroupId()).isNotNull();

        assertThat(inCmd.type()).isEqualTo(TransactionType.EXCHANGE_IN);
        assertThat(inCmd.accountId()).isEqualTo(USD_ACCOUNT_ID);
        assertThat(inCmd.amount()).isEqualByComparingTo("1000.0000");
        assertThat(inCmd.currencyCode()).isEqualTo("USD");
        assertThat(inCmd.categoryId()).isNull();
        assertThat(inCmd.scope()).isEqualTo(Scope.PERSONAL);
        assertThat(inCmd.attributedToUserId()).isEqualTo(ADULT_ID);
        assertThat(inCmd.currencyExchangeId()).isEqualTo(savedExchangeId);
        assertThat(inCmd.transferGroupId()).isEqualTo(outCmd.transferGroupId());

        verify(categoryQueryApi, never()).requireSystemCategory(any());
    }

    @Test
    void createWithFeeRecordsExpenseInFinancialCostsCategory() {
        AccountRef arsAccount = account(ARS_ACCOUNT_ID, "Banco ARS", "ARS");
        AccountRef usdAccount = account(USD_ACCOUNT_ID, "Caja USD", "USD");
        UUID savedExchangeId = UUID.randomUUID();
        UUID financialCostsCategoryId = UUID.randomUUID();

        when(accountQueryApi.requireUsable(ADULT, ARS_ACCOUNT_ID)).thenReturn(arsAccount);
        when(accountQueryApi.requireUsable(ADULT, USD_ACCOUNT_ID)).thenReturn(usdAccount);
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(defaultSettings());
        when(familyQueryApi.requireMember(FAMILY_ID, ADULT_ID))
                .thenReturn(new MemberRef(ADULT_ID, FAMILY_ID, "Ana Adult", Role.ADULTO, "#000000"));
        when(categoryQueryApi.requireSystemCategory("FINANCIAL_COSTS"))
                .thenReturn(new CategoryRef(
                        financialCostsCategoryId, "Costos financieros", "EXPENSE", "percent", null));
        when(exchangeRepository.saveAndFlush(any(CurrencyExchange.class))).thenAnswer(inv -> {
            CurrencyExchange ex = inv.getArgument(0);
            ReflectionTestUtils.setField(ex, "id", savedExchangeId);
            return ex;
        });

        ExchangeView view = service.create(
                ADULT,
                new CreateExchangeCommand(
                        ARS_ACCOUNT_ID,
                        USD_ACCOUNT_ID,
                        new BigDecimal("1185000"),
                        new BigDecimal("1000"),
                        null,
                        new BigDecimal("5000"),
                        null,
                        LocalDate.of(2026, 11, 20),
                        null));

        assertThat(view.feeAmount()).isEqualByComparingTo("5000.0000");
        assertThat(view.feeCurrency()).isEqualTo("ARS");

        ArgumentCaptor<NewTransactionCommand> cmdCaptor =
                ArgumentCaptor.forClass(NewTransactionCommand.class);
        verify(transactionCommandApi, times(3)).record(cmdCaptor.capture());

        NewTransactionCommand feeCmd = cmdCaptor.getAllValues().get(2);
        assertThat(feeCmd.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(feeCmd.accountId()).isEqualTo(ARS_ACCOUNT_ID);
        assertThat(feeCmd.categoryId()).isEqualTo(financialCostsCategoryId);
        assertThat(feeCmd.amount()).isEqualByComparingTo("5000.0000");
        assertThat(feeCmd.currencyCode()).isEqualTo("ARS");
        assertThat(feeCmd.currencyExchangeId()).isEqualTo(savedExchangeId);
        assertThat(feeCmd.transferGroupId()).isNull();
    }

    @Test
    void createRejectsSameSourceAndTargetAccount() {
        assertThatThrownBy(() -> service.create(
                        ADULT,
                        new CreateExchangeCommand(
                                ARS_ACCOUNT_ID,
                                ARS_ACCOUNT_ID,
                                new BigDecimal("1000"),
                                new BigDecimal("1000"),
                                null,
                                null,
                                null,
                                LocalDate.of(2026, 11, 20),
                                null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different");
    }

    @Test
    void deleteSoftDeletesAllLinkedTransactionsForAuthorOrAdult() {
        UUID exchangeId = UUID.randomUUID();
        UUID txOut = UUID.randomUUID();
        UUID txIn = UUID.randomUUID();
        UUID txFee = UUID.randomUUID();

        CurrencyExchange existing = CurrencyExchange.create(
                FAMILY_ID,
                CHILD_ID,
                ARS_ACCOUNT_ID,
                USD_ACCOUNT_ID,
                new BigDecimal("1185000"),
                "ARS",
                new BigDecimal("1000"),
                "USD",
                new BigDecimal("1185.000000"),
                new BigDecimal("5000"),
                "ARS",
                LocalDate.of(2026, 11, 20),
                null,
                Instant.now(FIXED_CLOCK));
        ReflectionTestUtils.setField(existing, "id", exchangeId);

        when(exchangeRepository.findByIdAndFamilyId(exchangeId, FAMILY_ID))
                .thenReturn(Optional.of(existing));
        when(exchangeReadQuery.hasActiveExchangeIn(FAMILY_ID, exchangeId)).thenReturn(true);
        when(exchangeReadQuery.findActiveTransactionIds(FAMILY_ID, exchangeId))
                .thenReturn(List.of(txOut, txIn, txFee));

        service.delete(ADULT, exchangeId);

        verify(transactionCommandApi).softDelete(FAMILY_ID, txOut);
        verify(transactionCommandApi).softDelete(FAMILY_ID, txIn);
        verify(transactionCommandApi).softDelete(FAMILY_ID, txFee);
    }

    @Test
    void deleteRejectsChildWhoIsNotAuthor() {
        UUID exchangeId = UUID.randomUUID();
        CurrencyExchange existing = CurrencyExchange.create(
                FAMILY_ID,
                ADULT_ID,
                ARS_ACCOUNT_ID,
                USD_ACCOUNT_ID,
                new BigDecimal("1185000"),
                "ARS",
                new BigDecimal("1000"),
                "USD",
                new BigDecimal("1185.000000"),
                BigDecimal.ZERO,
                null,
                LocalDate.of(2026, 11, 20),
                null,
                Instant.now(FIXED_CLOCK));
        ReflectionTestUtils.setField(existing, "id", exchangeId);

        when(exchangeRepository.findByIdAndFamilyId(exchangeId, FAMILY_ID))
                .thenReturn(Optional.of(existing));
        when(exchangeReadQuery.hasActiveExchangeIn(FAMILY_ID, exchangeId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(CHILD, exchangeId))
                .isInstanceOf(ForbiddenException.class);

        verify(transactionCommandApi, never()).softDelete(any(), any());
    }

    @Test
    void deleteThrowsNotFoundWhenAlreadyDeleted() {
        UUID exchangeId = UUID.randomUUID();
        CurrencyExchange existing = CurrencyExchange.create(
                FAMILY_ID,
                ADULT_ID,
                ARS_ACCOUNT_ID,
                USD_ACCOUNT_ID,
                new BigDecimal("1185000"),
                "ARS",
                new BigDecimal("1000"),
                "USD",
                new BigDecimal("1185.000000"),
                BigDecimal.ZERO,
                null,
                LocalDate.of(2026, 11, 20),
                null,
                Instant.now(FIXED_CLOCK));

        when(exchangeRepository.findByIdAndFamilyId(exchangeId, FAMILY_ID))
                .thenReturn(Optional.of(existing));
        when(exchangeReadQuery.hasActiveExchangeIn(FAMILY_ID, exchangeId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(ADULT, exchangeId))
                .isInstanceOf(NotFoundException.class);
    }

    private static AccountRef account(UUID id, String name, String currency) {
        return new AccountRef(id, FAMILY_ID, ADULT_ID, name, "BANK", currency, null, null);
    }

    private static FamilySettings defaultSettings() {
        return new FamilySettings(
                FAMILY_ID,
                "Familia Test",
                "ARS",
                "USD",
                "USD_MEP",
                ZoneId.of("America/Argentina/Buenos_Aires"),
                List.of("ARS", "USD"));
    }
}
