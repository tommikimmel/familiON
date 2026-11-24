package com.familion.finance.installments.application;

import java.util.UUID;

public record DueInstallmentRow(UUID familyId, UUID transactionId, UUID installmentPlanId) {}
