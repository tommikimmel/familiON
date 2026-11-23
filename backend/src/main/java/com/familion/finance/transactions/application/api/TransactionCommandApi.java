package com.familion.finance.transactions.application.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface TransactionCommandApi {

    UUID record(NewTransactionCommand cmd);

    void updateAmountAndDate(UUID familyId, UUID transactionId, BigDecimal amount, LocalDate date);

    void changeStatus(UUID familyId, UUID transactionId, TransactionStatus status);

    void softDelete(UUID familyId, UUID transactionId);
}
