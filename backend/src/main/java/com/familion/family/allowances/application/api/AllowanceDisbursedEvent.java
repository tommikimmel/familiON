package com.familion.family.allowances.application.api;

import java.math.BigDecimal;
import java.util.UUID;

public record AllowanceDisbursedEvent(
        UUID familyId,
        UUID allowanceId,
        UUID childUserId,
        BigDecimal amount,
        String currencyCode) {}
