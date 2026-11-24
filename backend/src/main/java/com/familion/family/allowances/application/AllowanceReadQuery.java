package com.familion.family.allowances.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AllowanceReadQuery {

    List<AllowanceReadRow> findVisibleForUser(
            UUID familyId, UUID userId, boolean adult, boolean includeInactive);

    Optional<AllowanceReadRow> findById(UUID familyId, UUID allowanceId);

    Map<UUID, LocalDate> findLastDisbursementDates(Collection<UUID> allowanceIds);
}
