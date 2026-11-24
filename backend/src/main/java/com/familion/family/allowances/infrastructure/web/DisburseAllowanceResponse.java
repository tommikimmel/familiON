package com.familion.family.allowances.infrastructure.web;

import com.familion.family.allowances.application.DisbursementResult;
import java.util.UUID;

public record DisburseAllowanceResponse(
        AllowanceResponse allowance, UUID outTransactionId, UUID inTransactionId) {

    public static DisburseAllowanceResponse from(DisbursementResult result) {
        return new DisburseAllowanceResponse(
                AllowanceResponse.from(result.allowance()),
                result.outTransactionId(),
                result.inTransactionId());
    }
}
