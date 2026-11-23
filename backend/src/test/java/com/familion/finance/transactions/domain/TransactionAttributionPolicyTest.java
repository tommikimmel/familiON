package com.familion.finance.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.finance.transactions.application.api.Scope;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionAttributionPolicyTest {

    private final UUID familyId = UUID.randomUUID();
    private final UUID adultId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();

    private final CurrentUser adult =
            new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    private final CurrentUser child =
            new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

    @Test
    void defaultsAttributedToCurrentUserWhenOmitted() {
        assertThat(TransactionAttributionPolicy.resolveAttributedTo(adult, null))
                .isEqualTo(adultId);
        assertThat(TransactionAttributionPolicy.resolveAttributedTo(child, null))
                .isEqualTo(childId);
    }

    @Test
    void adultCanAttributeToAnotherMemberButChildGetsForbidden() {
        assertThat(TransactionAttributionPolicy.resolveAttributedTo(adult, childId))
                .isEqualTo(childId);
        assertThat(TransactionAttributionPolicy.resolveAttributedTo(child, childId))
                .isEqualTo(childId);

        assertThatThrownBy(() -> TransactionAttributionPolicy.resolveAttributedTo(child, adultId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void childScopeIsForcedToPersonalOnCreateAndCannotSetFamilyOnEdit() {
        assertThat(TransactionAttributionPolicy.resolveScopeForCreate(adult, Scope.FAMILY))
                .isEqualTo(Scope.FAMILY);
        assertThat(TransactionAttributionPolicy.resolveScopeForCreate(adult, null))
                .isEqualTo(Scope.PERSONAL);
        assertThat(TransactionAttributionPolicy.resolveScopeForCreate(child, Scope.FAMILY))
                .isEqualTo(Scope.PERSONAL);

        assertThatThrownBy(
                        () -> TransactionAttributionPolicy.requireCanSetScope(child, Scope.FAMILY))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void childCannotChangeAttributionOnEdit() {
        assertThatThrownBy(
                        () -> TransactionAttributionPolicy.requireCanChangeAttribution(
                                child, childId, adultId))
                .isInstanceOf(ForbiddenException.class);
    }
}
