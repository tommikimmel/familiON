package com.familion.finance.recurring.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record ConfirmPaymentCommand(
        YearMonth month, BigDecimal amount, UUID accountId, LocalDate paymentDate) {}
