package com.familion.finance.transactions.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import com.familion.finance.accounts.application.api.AccountRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionCurrencyPolicyTest {

    private final UUID familyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void regularAccountRequiresMatchingCurrency() {
        AccountRef bankArs = account("BANK", "ARS");

        assertThatCode(() -> TransactionCurrencyPolicy.validateForAccount(
                        bankArs, "ARS", List.of("ARS", "USD")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> TransactionCurrencyPolicy.validateForAccount(
                        bankArs, "USD", List.of("ARS", "USD")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void creditCardAcceptsAnyEnabledFamilyCurrencyAndRejectsDisabledOnes() {
        AccountRef creditCard = account("CREDIT_CARD", "ARS");

        assertThatCode(() -> TransactionCurrencyPolicy.validateForAccount(
                        creditCard, "USD", List.of("ARS", "USD")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> TransactionCurrencyPolicy.validateForAccount(
                        creditCard, "EUR", List.of("ARS", "USD")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void transferRejectsSameAccountAndDifferentCurrencies() {
        AccountRef bankArs1 = account("BANK", "ARS");
        AccountRef bankArs2 = account("BANK", "ARS");
        AccountRef bankUsd = account("BANK", "USD");

        assertThatCode(() -> TransactionCurrencyPolicy.validateForTransfer(
                        bankArs1, bankArs2, "ARS", List.of("ARS", "USD")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> TransactionCurrencyPolicy.validateForTransfer(
                        bankArs1, bankArs1, "ARS", List.of("ARS", "USD")))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> TransactionCurrencyPolicy.validateForTransfer(
                        bankArs1, bankUsd, "ARS", List.of("ARS", "USD")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Use currency exchange");
    }

    private AccountRef account(String type, String currency) {
        return new AccountRef(
                UUID.randomUUID(), familyId, userId, "Acc " + type, type, currency, null, null);
    }
}
