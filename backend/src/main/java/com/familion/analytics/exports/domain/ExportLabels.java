package com.familion.analytics.exports.domain;

import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.time.Month;
import java.util.Objects;

public final class ExportLabels {

    private static final String[] MONTHS_ES = {
        "Enero",
        "Febrero",
        "Marzo",
        "Abril",
        "Mayo",
        "Junio",
        "Julio",
        "Agosto",
        "Septiembre",
        "Octubre",
        "Noviembre",
        "Diciembre"
    };

    private ExportLabels() {}

    public static String transactionType(TransactionType type) {
        Objects.requireNonNull(type, "type must not be null");
        return switch (type) {
            case EXPENSE -> "Gasto";
            case INCOME -> "Ingreso";
            case TRANSFER_OUT -> "Transferencia (salida)";
            case TRANSFER_IN -> "Transferencia (entrada)";
            case EXCHANGE_OUT -> "Cambio de divisa (salida)";
            case EXCHANGE_IN -> "Cambio de divisa (entrada)";
            case ALLOWANCE_OUT -> "Mensualidad (enviada)";
            case ALLOWANCE_IN -> "Mensualidad (recibida)";
        };
    }

    public static String transactionStatus(TransactionStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return switch (status) {
            case POSTED -> "Confirmado";
            case PROJECTED -> "Proyectado";
            case CANCELLED -> "Cancelado";
        };
    }

    public static String installment(Integer installmentNumber, Integer totalInstallments) {
        if (installmentNumber == null) {
            return "";
        }
        if (totalInstallments != null) {
            return installmentNumber + "/" + totalInstallments;
        }
        return String.valueOf(installmentNumber);
    }

    public static String monthName(Month month) {
        Objects.requireNonNull(month, "month must not be null");
        return MONTHS_ES[month.getValue() - 1];
    }
}
