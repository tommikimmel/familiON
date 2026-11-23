package com.familion.finance.transactions.domain;

import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.finance.transactions.application.api.Scope;
import java.util.Objects;
import java.util.UUID;

public final class TransactionAttributionPolicy {

    private TransactionAttributionPolicy() {}

    public static UUID resolveAttributedTo(CurrentUser user, UUID requestedAttributedToUserId) {
        Objects.requireNonNull(user, "user must not be null");
        UUID effective =
                requestedAttributedToUserId != null ? requestedAttributedToUserId : user.userId();
        if (!effective.equals(user.userId()) && !user.isAdult()) {
            throw new ForbiddenException(
                    "Only adult users can attribute transactions to another member");
        }
        return effective;
    }

    public static Scope resolveScopeForCreate(CurrentUser user, Scope requestedScope) {
        Objects.requireNonNull(user, "user must not be null");
        if (!user.isAdult()) {
            return Scope.PERSONAL;
        }
        return requestedScope != null ? requestedScope : Scope.PERSONAL;
    }

    public static void requireCanChangeAttribution(
            CurrentUser user, UUID currentAttributedToUserId, UUID requestedAttributedToUserId) {
        Objects.requireNonNull(user, "user must not be null");
        if (requestedAttributedToUserId != null
                && !requestedAttributedToUserId.equals(currentAttributedToUserId)
                && !user.isAdult()) {
            throw new ForbiddenException(
                    "Only adult users can change transaction attribution");
        }
    }

    public static void requireCanSetScope(CurrentUser user, Scope requestedScope) {
        Objects.requireNonNull(user, "user must not be null");
        if (requestedScope == Scope.FAMILY && !user.isAdult()) {
            throw new ForbiddenException("Only adult users can set FAMILY scope");
        }
    }
}
