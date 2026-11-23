package com.familion.economics.infrastructure.persistence;

import com.familion.economics.domain.EconomicIndex;
import com.familion.economics.domain.IndexCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EconomicIndexRepository extends JpaRepository<EconomicIndex, UUID> {

    Optional<EconomicIndex> findTopByIndexCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            IndexCode indexCode, LocalDate date);

    Optional<EconomicIndex> findTopByIndexCodeOrderByEffectiveDateDesc(IndexCode indexCode);

    Optional<EconomicIndex> findByIndexCodeAndEffectiveDate(
            IndexCode indexCode, LocalDate effectiveDate);

    List<EconomicIndex> findByIndexCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            IndexCode indexCode, LocalDate from, LocalDate to);

    List<EconomicIndex> findByIndexCodeOrderByEffectiveDateAsc(IndexCode indexCode);

    @Query(
            value =
                    """
                    SELECT DISTINCT ON (index_code) *
                    FROM economic_indices
                    ORDER BY index_code ASC, effective_date DESC
                    """,
            nativeQuery = true)
    List<EconomicIndex> findLatestPerIndexCode();

    /**
     * Upserts an API-sourced index value without ever overwriting an existing MANUAL entry.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO economic_indices (id, index_code, effective_date, value, source, created_at)
                    VALUES (gen_random_uuid(), :indexCode, :effectiveDate, :value, 'API', NOW())
                    ON CONFLICT (index_code, effective_date)
                    DO UPDATE SET value = EXCLUDED.value
                    WHERE economic_indices.source <> 'MANUAL'
                    """,
            nativeQuery = true)
    int upsertFromApi(
            @Param("indexCode") String indexCode,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("value") BigDecimal value);

    /**
     * Upserts a MANUAL index value, overwriting any existing entry for the same code and date.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO economic_indices (id, index_code, effective_date, value, source, created_at)
                    VALUES (gen_random_uuid(), :indexCode, :effectiveDate, :value, 'MANUAL', NOW())
                    ON CONFLICT (index_code, effective_date)
                    DO UPDATE SET value = EXCLUDED.value, source = 'MANUAL'
                    """,
            nativeQuery = true)
    int upsertManual(
            @Param("indexCode") String indexCode,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("value") BigDecimal value);
}
