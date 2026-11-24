package com.familion.finance.installments.application;

import com.familion.finance.installments.domain.CancelRemainingMode;
import java.time.LocalDate;

public record CancelRemainingCommand(CancelRemainingMode mode, LocalDate prepayDate) {}
