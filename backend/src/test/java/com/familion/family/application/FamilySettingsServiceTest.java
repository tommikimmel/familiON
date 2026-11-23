package com.familion.family.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.ConflictException;
import com.familion.common.error.NotFoundException;
import com.familion.family.domain.Family;
import com.familion.family.domain.FamilyCurrency;
import com.familion.family.infrastructure.persistence.FamilyCurrencyRepository;
import com.familion.family.infrastructure.persistence.FamilyRepository;
import com.familion.family.infrastructure.persistence.FamilyTransactionProbe;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FamilySettingsServiceTest {

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private FamilyCurrencyRepository familyCurrencyRepository;

    @Mock
    private FamilyTransactionProbe familyTransactionProbe;

    private FamilySettingsService service;

    @BeforeEach
    void setUp() {
        service = new FamilySettingsService(
                familyRepository, familyCurrencyRepository, familyTransactionProbe);
    }

    @Test
    void rejectsBaseCurrencyChangeWhenTransactionsExist() {
        UUID familyId = UUID.randomUUID();
        Family family = Family.createDefault("Familia Gomez");
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(familyTransactionProbe.hasTransactions(familyId)).thenReturn(true);

        UpdateFamilySettingsCommand command =
                new UpdateFamilySettingsCommand(null, "USD", null, null, null);

        assertThatThrownBy(() -> service.updateSettings(familyId, command))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Base currency cannot change once transactions exist");
        verify(familyRepository, never()).save(any());
    }

    @Test
    void allowsSettingsUpdateWithUnchangedBaseCurrencyEvenWhenTransactionsExist() {
        UUID familyId = UUID.randomUUID();
        Family family = Family.createDefault("Familia Gomez");
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(familyRepository.save(family)).thenReturn(family);
        when(familyCurrencyRepository
                        .findAllByFamilyIdAndActiveTrueOrderByCreatedAtAscCurrencyCodeAsc(any()))
                .thenReturn(List.of(FamilyCurrency.create(familyId, "ARS", null)));

        FamilyView updated = service.updateSettings(
                familyId,
                new UpdateFamilySettingsCommand(
                        "Familia Actualizada", "ARS", "EUR", "EUR_OFICIAL", "Europe/Madrid"));

        assertThat(updated.name()).isEqualTo("Familia Actualizada");
        assertThat(updated.baseCurrency()).isEqualTo("ARS");
        assertThat(updated.referenceCurrency()).isEqualTo("EUR");
        assertThat(updated.referenceRateSource()).isEqualTo("EUR_OFICIAL");
        assertThat(updated.timezone()).isEqualTo("Europe/Madrid");
        verify(familyTransactionProbe, never()).hasTransactions(any());
    }

    @Test
    void rejectsInvalidTimezoneAndRateSource() {
        UUID familyId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateSettings(
                        familyId,
                        new UpdateFamilySettingsCommand(null, null, null, null, "Mars/Olympus")))
                .isInstanceOf(InvalidFamilyRequestException.class);

        assertThatThrownBy(() -> service.updateSettings(
                        familyId,
                        new UpdateFamilySettingsCommand(null, null, null, "BTC_SPOT", null)))
                .isInstanceOf(InvalidFamilyRequestException.class);
    }

    @Test
    void rejectsEnablingAlreadyConfiguredCurrency() {
        UUID familyId = UUID.randomUUID();
        when(familyRepository.findById(familyId))
                .thenReturn(Optional.of(Family.createDefault("Familia")));
        when(familyCurrencyRepository.existsByFamilyIdAndCurrencyCode(familyId, "USD"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.enableCurrency(
                        familyId, new EnableFamilyCurrencyCommand("USD", "USD_MEP")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsDeactivatingBaseCurrency() {
        UUID familyId = UUID.randomUUID();
        Family family = Family.createDefault("Familia");
        FamilyCurrency ars = FamilyCurrency.create(familyId, "ARS", null);
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(familyCurrencyRepository.findByFamilyIdAndCurrencyCode(familyId, "ARS"))
                .thenReturn(Optional.of(ars));

        assertThatThrownBy(() -> service.updateCurrency(
                        familyId, "ARS", new UpdateFamilyCurrencyCommand(false, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Base currency cannot be deactivated");
    }

    @Test
    void throwsNotFoundWhenUpdatingMissingCurrency() {
        UUID familyId = UUID.randomUUID();
        when(familyRepository.findById(familyId))
                .thenReturn(Optional.of(Family.createDefault("Familia")));
        when(familyCurrencyRepository.findByFamilyIdAndCurrencyCode(familyId, "EUR"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCurrency(
                        familyId, "EUR", new UpdateFamilyCurrencyCommand(false, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
