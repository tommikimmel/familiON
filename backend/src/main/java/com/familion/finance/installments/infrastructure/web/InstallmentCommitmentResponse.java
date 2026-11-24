package com.familion.finance.installments.infrastructure.web;

import com.familion.finance.installments.application.InstallmentCommitmentView;
import java.math.BigDecimal;

public record InstallmentCommitmentResponse(
        String month, BigDecimal amount, String currencyCode) {

    public static InstallmentCommitmentResponse from(InstallmentCommitmentView view) {
        return new InstallmentCommitmentResponse(view.month(), view.amount(), view.currencyCode());
    }
}
