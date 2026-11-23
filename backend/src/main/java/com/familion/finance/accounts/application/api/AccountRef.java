package com.familion.finance.accounts.application.api;

import java.util.UUID;

public record AccountRef(
        UUID id,
        UUID familyId,
        UUID ownerUserId,
        String name,
        String accountType,
        String currencyCode,
        Integer closingDay,
        Integer dueDay) {}
