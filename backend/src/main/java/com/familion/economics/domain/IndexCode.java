package com.familion.economics.domain;

import java.util.Locale;
import java.util.Optional;

public enum IndexCode {
    USD_OFICIAL,
    USD_MEP,
    USD_BLUE,
    EUR_OFICIAL,
    EUR_BLUE,
    UVA,
    IPC_MENSUAL,
    IPC_ACUMULADO;

    public static Optional<IndexCode> fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(IndexCode.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
