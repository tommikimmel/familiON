package com.familion.finance.exchanges.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.DefaultVisibilityPolicy;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.security.View;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ExchangeQueryServiceTest {

    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final UUID ADULT_ID = UUID.randomUUID();
    private static final UUID CHILD_ID = UUID.randomUUID();

    private static final CurrentUser ADULT =
            new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    private static final CurrentUser CHILD_OWN_ONLY =
            new CurrentUser(CHILD_ID, FAMILY_ID, Role.HIJO, FamilyVisibility.OWN_ONLY);

    @Mock
    private ExchangeReadQuery readQuery;

    @Mock
    private FamilyQueryApi familyQueryApi;

    @Mock
    private EconomicsApi economicsApi;

    private ExchangeQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeQueryService(
                readQuery, new DefaultVisibilityPolicy(), familyQueryApi, economicsApi);
    }

    @Test
    void listClampsPageSizeToMax50() {
        when(readQuery.findPage(eq(FAMILY_ID), eq(ADULT_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(readQuery.count(FAMILY_ID, ADULT_ID)).thenReturn(0L);

        service.list(ADULT, View.PERSONAL, PageRequest.of(0, 200));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(readQuery).findPage(eq(FAMILY_ID), eq(ADULT_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void positionsRejectsChildOwnOnlyRequestingFamilyView() {
        assertThatThrownBy(() -> service.positions(CHILD_OWN_ONLY, View.FAMILY))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void positionsUsesReferenceRateSourceForUsdAndEurOficialForEur() {
        when(readQuery.findAllActiveChronological(FAMILY_ID, null))
                .thenReturn(List.of(
                        new ExchangePositionRow(
                                "ARS", new BigDecimal("1200000.0000"), "USD", new BigDecimal("1000.0000")),
                        new ExchangePositionRow(
                                "ARS", new BigDecimal("140000.0000"), "EUR", new BigDecimal("100.0000"))));
        when(familyQueryApi.getSettings(FAMILY_ID))
                .thenReturn(new FamilySettings(
                        FAMILY_ID,
                        "Familia",
                        "ARS",
                        "USD",
                        "USD_BLUE",
                        ZoneId.of("America/Argentina/Buenos_Aires"),
                        List.of("ARS", "USD", "EUR")));
        when(economicsApi.latestValue("USD_BLUE"))
                .thenReturn(Optional.of(new BigDecimal("1300.000000")));
        when(economicsApi.latestValue("EUR_OFICIAL"))
                .thenReturn(Optional.of(new BigDecimal("1450.000000")));

        List<CurrencyPositionView> positions = service.positions(ADULT, View.FAMILY);

        assertThat(positions).hasSize(2);
        CurrencyPositionView eur = positions.get(0);
        CurrencyPositionView usd = positions.get(1);

        assertThat(eur.currencyCode()).isEqualTo("EUR");
        assertThat(eur.currentRate()).isEqualByComparingTo("1450.000000");
        assertThat(eur.unrealizedResultBase()).isEqualByComparingTo("5000.0000");

        assertThat(usd.currencyCode()).isEqualTo("USD");
        assertThat(usd.currentRate()).isEqualByComparingTo("1300.000000");
        assertThat(usd.unrealizedResultBase()).isEqualByComparingTo("100000.0000");
    }
}
