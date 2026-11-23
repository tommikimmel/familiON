package com.familion.finance.transactions.domain;

import com.familion.common.error.BusinessRuleException;
import com.familion.finance.accounts.application.api.AccountRef;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TransactionCurrencyPolicy {

    private static final String CREDIT_CARD_TYPE = "CREDIT_CARD";

    private TransactionCurrencyPolicy() {}

    public static void validateForAccount(
            AccountRef account, String currencyCode, List<String> enabledCurrencies) {
        Objects.requireNonNull(account, "account must not be null");
        String normalized = normalize(currencyCode);

        if (CREDIT_CARD_TYPE.equals(account.accountType())) {
            boolean enabled = enabledCurrencies != null
                    && enabledCurrencies.stream().anyMatch(c -> normalized.equalsIgnoreCase(c));
            if (!enabled) {
                throw new BusinessRuleException(
                        "Currency " + normalized + " is not enabled for this family");
            }
            return;
        }

        if (!normalized.equalsIgnoreCase(account.currencyCode())) {
            throw new BusinessRuleException(
                    "Transaction currency "
                            + normalized
                            + " does not match account currency "
                            + account.currencyCode());
        }
    }

    public static void validateForTransfer(
            AccountRef sourceAccount,
            AccountRef targetAccount,
            String currencyCode,
            List<String> enabledCurrencies) {
        Objects.requireNonNull(sourceAccount, "sourceAccount must not be null");
        Objects.requireNonNull(targetAccount, "targetAccount must not be null");

        if (sourceAccount.id().equals(targetAccount.id())) {
            throw new BusinessRuleException("Source and target accounts must be different");
        }
        if (!sourceAccount.currencyCode().equalsIgnoreCase(targetAccount.currencyCode())) {
            throw new BusinessRuleException("Use currency exchange");
        }

        String normalized = normalize(currencyCode);
        if (!normalized.equalsIgnoreCase(sourceAccount.currencyCode())) {
            throw new BusinessRuleException(
                    "Transfer currency "
                            + normalized
                            + " must match account currency "
                            + sourceAccount.currencyCode());
        }
        validateForAccount(sourceAccount, normalized, enabledCurrencies);
        validateForAccount(targetAccount, normalized, enabledCurrencies);
    }

    private static String normalize(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new BusinessRuleException("Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }
}
