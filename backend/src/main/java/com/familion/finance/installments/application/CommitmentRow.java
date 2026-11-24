package com.familion.finance.installments.application;

import java.math.BigDecimal;

public record CommitmentRow(String month, BigDecimal amount, String currencyCode) {}
