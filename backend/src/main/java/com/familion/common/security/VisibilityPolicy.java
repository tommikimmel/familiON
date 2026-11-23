package com.familion.common.security;

public interface VisibilityPolicy {

    ViewAccess resolveForListing(CurrentUser u, View requested);

    ViewAccess resolveForTotals(CurrentUser u, View requested);
}
