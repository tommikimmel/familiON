package com.familion.finance.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.NotFoundException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionChangedEvent;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.EconomicSnapshot;
import com.familion.finance.transactions.infrastructure.persistence.EconomicSnapshotRepository;
import com.familion.finance.transactions.infrastructure.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class TransactionCommandApiIT extends IntegrationTestBase {

    @Autowired
    private TransactionCommandApi commandApi;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EconomicSnapshotRepository economicSnapshotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ApplicationEvents events;

    @BeforeEach
    void clearEconomicIndices() {
        jdbcTemplate.update("DELETE FROM economic_indices");
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }
    }

    @Test
    void recordUpdateChangeStatusAndSoftDeletePersistSnapshotsAndPublishEvents() {
        insertIndex("USD_MEP", LocalDate.of(2026, 10, 1), new BigDecimal("1000.000000"));
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 1), new BigDecimal("1250.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 10, 1), new BigDecimal("200.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("220.000000"));
        insertIndex("UVA", LocalDate.of(2026, 10, 1), new BigDecimal("1100.000000"));

        UUID familyId = fixtures.createFamily("Familia Cmd " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-cmd-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-cmd-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID accountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");

        UUID txId = commandApi.record(new NewTransactionCommand(
                familyId,
                adultId,
                childId,
                accountId,
                null,
                TransactionType.EXPENSE,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                new BigDecimal("10000"),
                "ARS",
                LocalDate.of(2026, 10, 15),
                "School books",
                "Math",
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        EconomicSnapshot initialSnapshot =
                economicSnapshotRepository.findById(txId).orElseThrow();
        assertThat(initialSnapshot.getBaseCurrencyAmount()).isEqualByComparingTo("10000.0000");
        assertThat(initialSnapshot.getAmountInReference()).isEqualByComparingTo("10.0000");
        assertThat(initialSnapshot.getReferenceExchangeRate()).isEqualByComparingTo("1000.000000");
        assertThat(initialSnapshot.getIpcIndexValue()).isEqualByComparingTo("200.000000");
        assertThat(initialSnapshot.getUvaValue()).isEqualByComparingTo("1100.000000");

        assertThat(events.stream(ExpenseAttributedEvent.class))
                .anyMatch(e -> e.transactionId().equals(txId)
                        && e.paidByUserId().equals(adultId)
                        && e.attributedToUserId().equals(childId));
        assertThat(events.stream(FamilyFinancialDataChangedEvent.class))
                .anyMatch(e -> e.familyId().equals(familyId));

        commandApi.updateAmountAndDate(
                familyId, txId, new BigDecimal("25000"), LocalDate.of(2026, 11, 10));

        EconomicSnapshot updatedSnapshot =
                economicSnapshotRepository.findById(txId).orElseThrow();
        assertThat(updatedSnapshot.getBaseCurrencyAmount()).isEqualByComparingTo("25000.0000");
        assertThat(updatedSnapshot.getAmountInReference()).isEqualByComparingTo("20.0000");
        assertThat(updatedSnapshot.getReferenceExchangeRate()).isEqualByComparingTo("1250.000000");
        assertThat(updatedSnapshot.getIpcIndexValue()).isEqualByComparingTo("220.000000");
        assertThat(events.stream(TransactionChangedEvent.class))
                .anyMatch(e -> e.transactionId().equals(txId)
                        && !e.deleted()
                        && e.amount().compareTo(new BigDecimal("25000.0000")) == 0
                        && e.date().equals(LocalDate.of(2026, 11, 10)));

        commandApi.changeStatus(familyId, txId, TransactionStatus.CANCELLED);
        assertThat(transactionRepository
                        .findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(TransactionStatus.CANCELLED);

        UUID otherFamilyId = fixtures.createFamily("Other Family " + UUID.randomUUID());
        assertThatThrownBy(() -> commandApi.softDelete(otherFamilyId, txId))
                .isInstanceOf(NotFoundException.class);

        commandApi.softDelete(familyId, txId);
        assertThat(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId))
                .isEmpty();
        assertThat(events.stream(TransactionChangedEvent.class))
                .anyMatch(e -> e.transactionId().equals(txId) && e.deleted());
    }

    private void insertIndex(String code, LocalDate effectiveDate, BigDecimal value) {
        jdbcTemplate.update(
                """
                INSERT INTO economic_indices (id, index_code, effective_date, value, source)
                VALUES (?, ?, ?, ?, 'MANUAL')
                """,
                UUID.randomUUID(),
                code,
                effectiveDate,
                value);
    }
}
