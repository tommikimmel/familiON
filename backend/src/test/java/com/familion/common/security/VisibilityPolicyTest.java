package com.familion.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.ForbiddenException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisibilityPolicyTest {

    private final VisibilityPolicy policy = new DefaultVisibilityPolicy();

    private static CurrentUser user(Role role, FamilyVisibility visibility) {
        return new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), role, visibility);
    }

    @Test
    void adultHasFullAccessForFamilyAndOwnForPersonal() {
        CurrentUser adult = user(Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        assertThat(adult.isAdult()).isTrue();
        assertThat(policy.resolveForListing(adult, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThat(policy.resolveForTotals(adult, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThat(policy.resolveForListing(adult, View.FAMILY)).isEqualTo(ViewAccess.FAMILY_FULL);
        assertThat(policy.resolveForTotals(adult, View.FAMILY)).isEqualTo(ViewAccess.FAMILY_FULL);
    }

    @Test
    void childWithOwnOnlyCanOnlyAccessPersonalView() {
        CurrentUser child = user(Role.HIJO, FamilyVisibility.OWN_ONLY);

        assertThat(child.isAdult()).isFalse();
        assertThat(policy.resolveForListing(child, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThat(policy.resolveForTotals(child, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThatThrownBy(() -> policy.resolveForListing(child, View.FAMILY))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policy.resolveForTotals(child, View.FAMILY))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void childWithFamilySummaryCanViewTotalsOnlyNotListing() {
        CurrentUser child = user(Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);

        assertThat(policy.resolveForListing(child, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThat(policy.resolveForTotals(child, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThatThrownBy(() -> policy.resolveForListing(child, View.FAMILY))
                .isInstanceOf(ForbiddenException.class);
        assertThat(policy.resolveForTotals(child, View.FAMILY))
                .isEqualTo(ViewAccess.FAMILY_TOTALS_ONLY);
    }

    @Test
    void childWithFamilyFullHasFullFamilyAccessAndOwnForPersonal() {
        CurrentUser child = user(Role.HIJO, FamilyVisibility.FAMILY_FULL);

        assertThat(policy.resolveForListing(child, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThat(policy.resolveForTotals(child, View.PERSONAL)).isEqualTo(ViewAccess.OWN);
        assertThat(policy.resolveForListing(child, View.FAMILY)).isEqualTo(ViewAccess.FAMILY_FULL);
        assertThat(policy.resolveForTotals(child, View.FAMILY)).isEqualTo(ViewAccess.FAMILY_FULL);
    }
}
