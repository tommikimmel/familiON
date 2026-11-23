package com.familion.economics.application;

import com.familion.economics.domain.EconomicIndex;
import com.familion.economics.domain.IndexCode;
import com.familion.economics.domain.IpcChainCalculator;
import com.familion.economics.domain.IpcChainCalculator.AccumulatedIpc;
import com.familion.economics.domain.IpcChainCalculator.MonthlyIpc;
import com.familion.economics.infrastructure.external.ArgentinaDatosClient;
import com.familion.economics.infrastructure.external.DolarApiClient;
import com.familion.economics.infrastructure.external.ExternalIndexPoint;
import com.familion.economics.infrastructure.persistence.EconomicIndexBatchRepository;
import com.familion.economics.infrastructure.persistence.EconomicIndexBatchRepository.UpsertEntry;
import com.familion.economics.infrastructure.persistence.EconomicIndexRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Synchronizes economic indices from external Argentine APIs on a schedule and on startup backfill.
 *
 * <p>External errors are logged at WARN level and never propagated so users are never blocked when
 * upstream APIs or internet connectivity are unavailable (RNF-06). API upserts never overwrite
 * {@code MANUAL} entries.
 */
@Service
public class EconomicsSyncService {

    private static final Logger log = LoggerFactory.getLogger(EconomicsSyncService.class);
    private static final int RECENT_WINDOW_DAYS = 60;
    private static final int BACKFILL_YEARS = 2;
    private static final String SYNC_ON_STARTUP_PROPERTY = "familion.economics.sync-on-startup";

    private static final Map<String, IndexCode> HISTORICAL_DOLLAR_CASAS = Map.of(
            "oficial", IndexCode.USD_OFICIAL,
            "blue", IndexCode.USD_BLUE,
            "bolsa", IndexCode.USD_MEP);

    private final DolarApiClient dolarApiClient;
    private final ArgentinaDatosClient argentinaDatosClient;
    private final EconomicIndexRepository repository;
    private final EconomicIndexBatchRepository batchRepository;
    private final IpcChainCalculator ipcChainCalculator;
    private final Clock clock;
    private final Environment environment;
    private final boolean syncOnStartup;

    public EconomicsSyncService(
            DolarApiClient dolarApiClient,
            ArgentinaDatosClient argentinaDatosClient,
            EconomicIndexRepository repository,
            EconomicIndexBatchRepository batchRepository,
            IpcChainCalculator ipcChainCalculator,
            Clock clock,
            Environment environment,
            @Value("${familion.economics.sync-on-startup:true}") boolean syncOnStartup) {
        this.dolarApiClient = dolarApiClient;
        this.argentinaDatosClient = argentinaDatosClient;
        this.repository = repository;
        this.batchRepository = batchRepository;
        this.ipcChainCalculator = ipcChainCalculator;
        this.clock = clock;
        this.environment = environment;
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!shouldRunStartupSync()) {
            return;
        }
        Thread.ofVirtual().name("economics-startup-sync").start(this::backfillIfEmpty);
    }

    /**
     * Scheduled synchronization of latest quotes (DolarApi) plus IPC and UVA for the last 60 days.
     * Never propagates exceptions.
     */
    @Scheduled(
            cron = "${ECON_SYNC_CRON:0 0 */6 * * *}",
            zone = "America/Argentina/Buenos_Aires")
    public void syncLatest() {
        try {
            LocalDate todayAr = todayArgentina();
            LocalDate recentCutoff = todayAr.minusDays(RECENT_WINDOW_DAYS);

            syncPoints("DolarApi latest dollars", dolarApiClient::fetchLatestDollars);
            syncPoints(
                    "DolarApi latest EUR",
                    () -> dolarApiClient.fetchLatestEur().map(List::of).orElseGet(List::of));

            boolean ipcSaved = syncPoints(
                    "ArgentinaDatos recent IPC",
                    () -> argentinaDatosClient.fetchInflation(recentCutoff));
            if (ipcSaved) {
                recalculateIpcAccumulated();
            }

            syncPoints(
                    "ArgentinaDatos recent UVA", () -> argentinaDatosClient.fetchUva(recentCutoff));
        } catch (Exception ex) {
            log.warn("Unexpected failure during scheduled economic sync: {}", ex.getMessage());
        }
    }

    public void backfillIfEmpty() {
        try {
            if (repository.count() > 0) {
                log.debug("Skipping economic indices backfill because table already has data");
                return;
            }
            log.info("Economic indices table is empty; starting {}-year backfill", BACKFILL_YEARS);
            backfillLastTwoYears();
        } catch (Exception ex) {
            log.warn("Startup economic indices backfill failed: {}", ex.getMessage());
        }
    }

    public void backfillLastTwoYears() {
        try {
            LocalDate from = todayArgentina().minusYears(BACKFILL_YEARS);

            for (Map.Entry<String, IndexCode> entry : HISTORICAL_DOLLAR_CASAS.entrySet()) {
                String casa = entry.getKey();
                IndexCode code = entry.getValue();
                syncPoints(
                        "ArgentinaDatos historical dollars (" + casa + ")",
                        () -> argentinaDatosClient.fetchHistoricalDollars(casa, code, from));
            }

            syncPoints("DolarApi latest dollars", dolarApiClient::fetchLatestDollars);
            syncPoints(
                    "DolarApi latest EUR",
                    () -> dolarApiClient.fetchLatestEur().map(List::of).orElseGet(List::of));

            boolean ipcSaved = syncPoints(
                    "ArgentinaDatos historical IPC",
                    () -> argentinaDatosClient.fetchInflation(from));
            if (ipcSaved) {
                recalculateIpcAccumulated();
            }

            syncPoints(
                    "ArgentinaDatos historical UVA", () -> argentinaDatosClient.fetchUva(from));
        } catch (Exception ex) {
            log.warn("Unexpected failure during economic indices backfill: {}", ex.getMessage());
        }
    }

    /**
     * Recomputes and persists the full {@link IndexCode#IPC_ACUMULADO} chain from all stored
     * {@link IndexCode#IPC_MENSUAL} rows.
     */
    public void recalculateIpcAccumulated() {
        List<EconomicIndex> monthlyRows =
                repository.findByIndexCodeOrderByEffectiveDateAsc(IndexCode.IPC_MENSUAL);
        if (monthlyRows.isEmpty()) {
            return;
        }
        List<MonthlyIpc> monthlySeries = monthlyRows.stream()
                .map(row -> new MonthlyIpc(row.getEffectiveDate(), row.getValue()))
                .toList();
        List<AccumulatedIpc> accumulated = ipcChainCalculator.calculate(monthlySeries);
        List<UpsertEntry> entries = accumulated.stream()
                .map(item -> new UpsertEntry(
                        IndexCode.IPC_ACUMULADO, item.effectiveDate(), item.value()))
                .toList();
        batchRepository.batchUpsertFromApi(entries);
    }

    private boolean syncPoints(String sourceName, Supplier<List<ExternalIndexPoint>> fetcher) {
        try {
            List<ExternalIndexPoint> points = fetcher.get();
            if (points == null || points.isEmpty()) {
                return false;
            }
            List<UpsertEntry> entries = points.stream()
                    .map(p -> new UpsertEntry(p.indexCode(), p.effectiveDate(), p.value()))
                    .toList();
            batchRepository.batchUpsertFromApi(entries);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync {}: {}", sourceName, ex.getMessage());
            return false;
        }
    }

    private boolean shouldRunStartupSync() {
        if (!syncOnStartup) {
            return false;
        }
        if (environment != null
                && environment.acceptsProfiles(Profiles.of("test"))
                && !environment.containsProperty(SYNC_ON_STARTUP_PROPERTY)) {
            return false;
        }
        return true;
    }

    private LocalDate todayArgentina() {
        return LocalDate.now(clock.withZone(DolarApiClient.ARGENTINA_ZONE));
    }
}
