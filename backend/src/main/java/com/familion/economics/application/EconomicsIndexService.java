package com.familion.economics.application;

import com.familion.common.money.Money;
import com.familion.economics.application.EconomicIndexCache.LatestLookup;
import com.familion.economics.domain.EconomicIndex;
import com.familion.economics.domain.IndexCode;
import com.familion.economics.domain.IndexSource;
import com.familion.economics.infrastructure.persistence.EconomicIndexRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for querying latest/historical economic indices and recording manual
 * overrides.
 *
 * <p>Economic indices are global to the deployment (not family-scoped). Reads use Redis caching
 * with transparent PostgreSQL fallback.
 */
@Service
public class EconomicsIndexService {

    private static final int MAX_HISTORY_YEARS = 2;

    private final EconomicIndexRepository repository;
    private final EconomicIndexCache cache;
    private final EconomicsSyncService syncService;

    public EconomicsIndexService(
            EconomicIndexRepository repository,
            EconomicIndexCache cache,
            EconomicsSyncService syncService) {
        this.repository = repository;
        this.cache = cache;
        this.syncService = syncService;
    }

    @Transactional(readOnly = true)
    public List<IndexEntry> getLatestAll() {
        Map<IndexCode, IndexEntry> cached = new EnumMap<>(IndexCode.class);
        boolean anyMiss = false;

        for (IndexCode code : IndexCode.values()) {
            LatestLookup lookup = cache.getLatest(code);
            if (lookup instanceof LatestLookup.Hit hit) {
                if (hit.value() != null) {
                    cached.put(code, hit.value());
                }
            } else {
                anyMiss = true;
                break;
            }
        }

        if (!anyMiss) {
            return cached.values().stream()
                    .sorted(Comparator.comparing(entry -> entry.indexCode().name()))
                    .toList();
        }

        List<EconomicIndex> latestFromDb = repository.findLatestPerIndexCode();
        Map<IndexCode, IndexEntry> byCode = new EnumMap<>(IndexCode.class);
        for (EconomicIndex entity : latestFromDb) {
            IndexEntry entry = IndexEntry.fromEntity(entity);
            byCode.put(entry.indexCode(), entry);
        }

        for (IndexCode code : IndexCode.values()) {
            cache.putLatest(code, byCode.get(code));
        }

        return byCode.values().stream()
                .sorted(Comparator.comparing(entry -> entry.indexCode().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IndexEntry> getHistory(IndexCode indexCode, LocalDate from, LocalDate to) {
        Objects.requireNonNull(indexCode, "indexCode must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        if (to.isBefore(from)) {
            throw new InvalidEconomicsQueryException("from", "'from' date must not be after 'to' date");
        }
        if (to.isAfter(from.plusYears(MAX_HISTORY_YEARS))) {
            throw new InvalidEconomicsQueryException(
                    "to", "Date range must not exceed " + MAX_HISTORY_YEARS + " years");
        }

        List<YearMonth> months = enumerateMonths(from, to);
        Map<YearMonth, List<IndexEntry>> resolved = new LinkedHashMap<>();
        List<YearMonth> missingMonths = new ArrayList<>();

        for (YearMonth month : months) {
            Optional<List<IndexEntry>> hit = cache.getMonthlyHistory(indexCode, month);
            if (hit.isPresent()) {
                resolved.put(month, hit.get());
            } else {
                missingMonths.add(month);
            }
        }

        if (!missingMonths.isEmpty()) {
            LocalDate fetchStart = missingMonths.getFirst().atDay(1);
            LocalDate fetchEnd = missingMonths.getLast().atEndOfMonth();
            List<EconomicIndex> rows =
                    repository.findByIndexCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                            indexCode, fetchStart, fetchEnd);

            Map<YearMonth, List<IndexEntry>> grouped = new LinkedHashMap<>();
            for (YearMonth month : missingMonths) {
                grouped.put(month, new ArrayList<>());
            }
            for (EconomicIndex row : rows) {
                YearMonth ym = YearMonth.from(row.getEffectiveDate());
                List<IndexEntry> bucket = grouped.get(ym);
                if (bucket != null) {
                    bucket.add(IndexEntry.fromEntity(row));
                }
            }

            for (Map.Entry<YearMonth, List<IndexEntry>> entry : grouped.entrySet()) {
                List<IndexEntry> immutableBucket = List.copyOf(entry.getValue());
                resolved.put(entry.getKey(), immutableBucket);
                cache.putMonthlyHistory(indexCode, entry.getKey(), immutableBucket);
            }
        }

        List<IndexEntry> result = new ArrayList<>();
        for (YearMonth month : months) {
            List<IndexEntry> monthEntries = resolved.getOrDefault(month, List.of());
            for (IndexEntry entry : monthEntries) {
                if (!entry.effectiveDate().isBefore(from) && !entry.effectiveDate().isAfter(to)) {
                    result.add(entry);
                }
            }
        }
        result.sort(Comparator.comparing(IndexEntry::effectiveDate));
        return List.copyOf(result);
    }

    /**
     * Records a deployment-wide manual index override with {@link IndexSource#MANUAL}.
     *
     * <p>For {@link IndexCode#IPC_MENSUAL}, {@code effectiveDate} is normalized to the first day of
     * the month and the full {@link IndexCode#IPC_ACUMULADO} chain is recalculated.
     */
    @Transactional
    public IndexEntry saveManual(IndexCode indexCode, LocalDate effectiveDate, BigDecimal value) {
        Objects.requireNonNull(indexCode, "indexCode must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(value, "value must not be null");

        BigDecimal scaledValue = Money.rate(value);
        if (scaledValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEconomicsQueryException("value", "Value must be greater than zero");
        }

        LocalDate normalizedDate =
                indexCode == IndexCode.IPC_MENSUAL ? effectiveDate.withDayOfMonth(1) : effectiveDate;

        repository.upsertManual(indexCode.name(), normalizedDate, scaledValue);
        cache.invalidate(indexCode, Set.of(YearMonth.from(normalizedDate)));

        if (indexCode == IndexCode.IPC_MENSUAL) {
            syncService.recalculateIpcAccumulated();
        }

        return new IndexEntry(indexCode, normalizedDate, scaledValue, IndexSource.MANUAL);
    }

    private static List<YearMonth> enumerateMonths(LocalDate from, LocalDate to) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!current.isAfter(end)) {
            months.add(current);
            current = current.plusMonths(1);
        }
        return months;
    }
}
