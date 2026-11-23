package com.familion.economics.application;

import com.familion.economics.domain.IndexCode;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cache port for latest and monthly historical economic indices.
 *
 * <p>Implementations must degrade gracefully when the backing cache is unavailable, returning cache
 * misses instead of throwing exceptions.
 */
public interface EconomicIndexCache {

    sealed interface LatestLookup permits LatestLookup.Hit, LatestLookup.Miss {
        record Hit(IndexEntry value) implements LatestLookup {}

        record Miss() implements LatestLookup {}
    }

    LatestLookup getLatest(IndexCode indexCode);

    void putLatest(IndexCode indexCode, IndexEntry value);

    Optional<List<IndexEntry>> getMonthlyHistory(IndexCode indexCode, YearMonth yearMonth);

    void putMonthlyHistory(IndexCode indexCode, YearMonth yearMonth, List<IndexEntry> entries);

    void invalidate(IndexCode indexCode, Set<YearMonth> affectedMonths);

    void invalidateAllFor(IndexCode indexCode);
}
