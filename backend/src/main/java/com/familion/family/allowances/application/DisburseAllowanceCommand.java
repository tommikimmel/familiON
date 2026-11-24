package com.familion.family.allowances.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DisburseAllowanceCommand(LocalDate date, BigDecimal amount) {}
