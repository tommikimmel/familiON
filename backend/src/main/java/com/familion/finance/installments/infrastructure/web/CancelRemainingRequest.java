package com.familion.finance.installments.infrastructure.web;

import com.familion.finance.installments.domain.CancelRemainingMode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CancelRemainingRequest(@NotNull CancelRemainingMode mode, LocalDate prepayDate) {}
