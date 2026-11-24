package com.familion.family.allowances.application;

import java.util.UUID;

public record DisbursementResult(
        AllowanceView allowance, UUID outTransactionId, UUID inTransactionId) {}
