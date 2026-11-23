package com.familion.common.security;

import com.familion.common.error.ForbiddenException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DefaultVisibilityPolicy implements VisibilityPolicy {

    @Override
    public ViewAccess resolveForListing(CurrentUser u, View requested) {
        Objects.requireNonNull(u, "currentUser must not be null");
        Objects.requireNonNull(requested, "requested view must not be null");

        if (requested == View.PERSONAL) {
            return ViewAccess.OWN;
        }
        if (u.isAdult()) {
            return ViewAccess.FAMILY_FULL;
        }
        if (u.visibility() == FamilyVisibility.FAMILY_FULL) {
            return ViewAccess.FAMILY_FULL;
        }
        throw new ForbiddenException("User is not allowed to view family listings");
    }

    @Override
    public ViewAccess resolveForTotals(CurrentUser u, View requested) {
        Objects.requireNonNull(u, "currentUser must not be null");
        Objects.requireNonNull(requested, "requested view must not be null");

        if (requested == View.PERSONAL) {
            return ViewAccess.OWN;
        }
        if (u.isAdult()) {
            return ViewAccess.FAMILY_FULL;
        }
        return switch (u.visibility()) {
            case FAMILY_FULL -> ViewAccess.FAMILY_FULL;
            case FAMILY_SUMMARY -> ViewAccess.FAMILY_TOTALS_ONLY;
            case OWN_ONLY -> throw new ForbiddenException("User is not allowed to view family totals");
        };
    }
}
