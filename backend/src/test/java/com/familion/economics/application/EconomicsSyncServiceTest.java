package com.familion.economics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.economics.domain.EconomicIndex;
import com.familion.economics.domain.IndexCode;
import com.familion.economics.domain.IndexSource;
import com.familion.economics.domain.IpcChainCalculator;
import com.familion.economics.infrastructure.external.ArgentinaDatosClient;
import com.familion.economics.infrastructure.external.DolarApiClient;
import com.familion.economics.infrastructure.external.ExternalIndexPoint;
import com.familion.economics.infrastructure.persistence.EconomicIndexBatchRepository;
import com.familion.economics.infrastructure.persistence.EconomicIndexBatchRepository.UpsertEntry;
import com.familion.economics.infrastructure.persistence.EconomicIndexRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.HttpServerErrorException;

@ExtendWith(MockitoExtension.class)
class EconomicsSyncServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private DolarApiClient dolarApiClient;

    @Mock
    private ArgentinaDatosClient argentinaDatosClient;

    @Mock
    private EconomicIndexRepository repository;

    @Mock
    private EconomicIndexBatchRepository batchRepository;

    @Mock
    private EconomicIndexCache cache;

    private EconomicsSyncService service;

    @BeforeEach
    void setUp() {
        service = new EconomicsSyncService(
                dolarApiClient,
                argentinaDatosClient,
                repository,
                batchRepository,
                cache,
                new IpcChainCalculator(),
                FIXED_CLOCK,
                new MockEnvironment(),
                true);
    }

    @Test
    void continuesSyncWhenDolarApiReturns500AndRecalculatesIpcAccumulated() {
        when(dolarApiClient.fetchLatestDollars())
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        when(dolarApiClient.fetchLatestEur())
                .thenReturn(
                        Optional.of(
                                new ExternalIndexPoint(
                                        IndexCode.EUR_OFICIAL,
                                        LocalDate.of(2026, 11, 23),
                                        new BigDecimal("1735.630000"))));
        when(argentinaDatosClient.fetchInflation(any(LocalDate.class)))
                .thenReturn(
                        List.of(
                                new ExternalIndexPoint(
                                        IndexCode.IPC_MENSUAL,
                                        LocalDate.of(2026, 10, 1),
                                        new BigDecimal("2.500000"))));
        when(repository.findByIndexCodeOrderByEffectiveDateAsc(IndexCode.IPC_MENSUAL))
                .thenReturn(
                        List.of(
                                new EconomicIndex(
                                        IndexCode.IPC_MENSUAL,
                                        LocalDate.of(2026, 10, 1),
                                        new BigDecimal("2.500000"),
                                        IndexSource.API)));
        when(argentinaDatosClient.fetchUva(any(LocalDate.class)))
                .thenReturn(
                        List.of(
                                new ExternalIndexPoint(
                                        IndexCode.UVA,
                                        LocalDate.of(2026, 11, 23),
                                        new BigDecimal("2206.780000"))));

        assertThatCode(() -> service.syncLatest()).doesNotThrowAnyException();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UpsertEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository, times(4)).batchUpsertFromApi(captor.capture());

        List<UpsertEntry> allSaved =
                captor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(allSaved)
                .contains(
                        new UpsertEntry(
                                IndexCode.EUR_OFICIAL,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1735.630000")),
                        new UpsertEntry(
                                IndexCode.IPC_MENSUAL,
                                LocalDate.of(2026, 10, 1),
                                new BigDecimal("2.500000")),
                        new UpsertEntry(
                                IndexCode.IPC_ACUMULADO,
                                LocalDate.of(2026, 10, 1),
                                new BigDecimal("102.500000")),
                        new UpsertEntry(
                                IndexCode.UVA,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("2206.780000")));
        verify(cache).invalidate(IndexCode.EUR_OFICIAL, Set.of(YearMonth.of(2026, 11)));
        verify(cache).invalidate(IndexCode.IPC_MENSUAL, Set.of(YearMonth.of(2026, 10)));
        verify(cache).invalidate(IndexCode.UVA, Set.of(YearMonth.of(2026, 11)));
        verify(cache).invalidateAllFor(IndexCode.IPC_ACUMULADO);
    }

    @Test
    void skipsBackfillWhenTableAlreadyHasRows() {
        when(repository.count()).thenReturn(10L);

        service.backfillIfEmpty();

        verify(argentinaDatosClient, never()).fetchHistoricalDollars(any(), any(), any());
        verify(dolarApiClient, never()).fetchLatestDollars();
    }

    @Test
    void runsTwoYearBackfillWhenTableIsEmptyAndSurvivesUpstreamErrors() {
        when(repository.count()).thenReturn(0L);
        when(argentinaDatosClient.fetchHistoricalDollars(any(), any(), any()))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(dolarApiClient.fetchLatestDollars()).thenReturn(List.of());
        when(dolarApiClient.fetchLatestEur()).thenReturn(Optional.empty());
        when(argentinaDatosClient.fetchInflation(any())).thenReturn(List.of());
        when(argentinaDatosClient.fetchUva(any())).thenReturn(List.of());

        assertThatCode(() -> service.backfillIfEmpty()).doesNotThrowAnyException();

        verify(argentinaDatosClient, times(3))
                .fetchHistoricalDollars(any(), any(), any(LocalDate.class));
        verify(argentinaDatosClient).fetchInflation(LocalDate.of(2024, 11, 23));
        verify(argentinaDatosClient).fetchUva(LocalDate.of(2024, 11, 23));
    }
}
