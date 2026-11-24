package com.familion.finance.exchanges.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.exchanges.application.CreateExchangeCommand;
import com.familion.finance.exchanges.application.ExchangeService;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class ExchangeAtomicityIT extends IntegrationTestBase {

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private TransactionCommandApi transactionCommandApi;

    @AfterEach
    void tearDown() {
        reset(transactionCommandApi);
    }

    @Test
    void rollsBackExchangeAndOutLegWhenInLegFails() {
        UUID familyId = fixtures.createFamily("Familia Atomic In " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-atomic-in-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID arsAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID usdAccountId = fixtures.createAccount(familyId, adultId, "BANK", "USD");

        doThrow(new IllegalStateException("Simulated EXCHANGE_IN failure"))
                .when(transactionCommandApi)
                .record(argThat(cmd -> cmd != null && cmd.type() == TransactionType.EXCHANGE_IN));

        CurrentUser user =
                new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        assertThatThrownBy(() -> exchangeService.create(
                        user,
                        new CreateExchangeCommand(
                                arsAccountId,
                                usdAccountId,
                                new BigDecimal("1185000"),
                                new BigDecimal("1000"),
                                null,
                                null,
                                null,
                                LocalDate.of(2026, 11, 20),
                                null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated EXCHANGE_IN failure");

        Integer exchanges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_exchanges WHERE family_id = ?",
                Integer.class,
                familyId);
        Integer transactions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ?",
                Integer.class,
                familyId);

        assertThat(exchanges).isZero();
        assertThat(transactions).isZero();
    }

    @Test
    void rollsBackExchangeAndBothLegsWhenFeeExpenseFails() {
        UUID familyId = fixtures.createFamily("Familia Atomic Fee " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-atomic-fee-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID arsAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID usdAccountId = fixtures.createAccount(familyId, adultId, "BANK", "USD");

        doThrow(new IllegalStateException("Simulated fee EXPENSE failure"))
                .when(transactionCommandApi)
                .record(argThat(cmd -> cmd != null && cmd.type() == TransactionType.EXPENSE));

        CurrentUser user =
                new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        assertThatThrownBy(() -> exchangeService.create(
                        user,
                        new CreateExchangeCommand(
                                arsAccountId,
                                usdAccountId,
                                new BigDecimal("1185000"),
                                new BigDecimal("1000"),
                                null,
                                new BigDecimal("5000"),
                                "ARS",
                                LocalDate.of(2026, 11, 20),
                                null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated fee EXPENSE failure");

        Integer exchanges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_exchanges WHERE family_id = ?",
                Integer.class,
                familyId);
        Integer transactions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ?",
                Integer.class,
                familyId);

        assertThat(exchanges).isZero();
        assertThat(transactions).isZero();
    }
}
