package com.familion.finance.installments.application;

import java.math.BigDecimal;

public record InstallmentCommitmentView(String month, BigDecimal amount, String currencyCode) {}
