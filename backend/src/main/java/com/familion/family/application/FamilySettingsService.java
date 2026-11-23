package com.familion.family.application;

import com.familion.common.error.ConflictException;
import com.familion.common.error.NotFoundException;
import com.familion.family.domain.Family;
import com.familion.family.domain.FamilyCatalog;
import com.familion.family.domain.FamilyCurrency;
import com.familion.family.infrastructure.persistence.FamilyCurrencyRepository;
import com.familion.family.infrastructure.persistence.FamilyRepository;
import com.familion.family.infrastructure.persistence.FamilyTransactionProbe;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilySettingsService {

    private final FamilyRepository familyRepository;
    private final FamilyCurrencyRepository familyCurrencyRepository;
    private final FamilyTransactionProbe familyTransactionProbe;

    public FamilySettingsService(
            FamilyRepository familyRepository,
            FamilyCurrencyRepository familyCurrencyRepository,
            FamilyTransactionProbe familyTransactionProbe) {
        this.familyRepository = familyRepository;
        this.familyCurrencyRepository = familyCurrencyRepository;
        this.familyTransactionProbe = familyTransactionProbe;
    }

    @Transactional(readOnly = true)
    public FamilyView getFamily(UUID familyId) {
        Family family = requireFamily(familyId);
        return toFamilyView(family);
    }

    @Transactional
    public FamilyView updateSettings(UUID familyId, UpdateFamilySettingsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateSettingsCommand(command);

        Family family = requireFamily(familyId);

        if (command.baseCurrency() != null
                && !command.baseCurrency().equals(family.getBaseCurrency())) {
            if (familyTransactionProbe.hasTransactions(familyId)) {
                throw new ConflictException("Base currency cannot change once transactions exist");
            }
            ensureBaseCurrencyEnabled(familyId, command.baseCurrency());
        }

        family.updateSettings(
                command.name(),
                command.baseCurrency(),
                command.referenceCurrency(),
                command.referenceRateSource(),
                command.timezone());
        Family saved = familyRepository.save(family);
        return toFamilyView(saved);
    }

    @Transactional(readOnly = true)
    public List<FamilyCurrencyView> listCurrencies(UUID familyId) {
        requireFamily(familyId);
        return familyCurrencyRepository
                .findAllByFamilyIdOrderByCreatedAtAscCurrencyCodeAsc(familyId)
                .stream()
                .map(FamilySettingsService::toCurrencyView)
                .toList();
    }

    @Transactional
    public FamilyCurrencyView enableCurrency(UUID familyId, EnableFamilyCurrencyCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireFamily(familyId);

        if (!FamilyCatalog.isValidCurrency(command.currencyCode())) {
            throw new InvalidFamilyRequestException(
                    "currencyCode", "Currency code must be one of ARS, USD, EUR, USDT");
        }
        if (command.preferredRateSource() != null
                && !FamilyCatalog.isValidRateSource(command.preferredRateSource())) {
            throw new InvalidFamilyRequestException(
                    "preferredRateSource",
                    "Preferred rate source must be one of USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA");
        }

        if (familyCurrencyRepository.existsByFamilyIdAndCurrencyCode(
                familyId, command.currencyCode())) {
            throw new ConflictException(
                    "Currency " + command.currencyCode() + " is already configured for this family");
        }

        FamilyCurrency created = FamilyCurrency.create(
                familyId, command.currencyCode(), command.preferredRateSource());
        return toCurrencyView(familyCurrencyRepository.save(created));
    }

    @Transactional
    public FamilyCurrencyView updateCurrency(
            UUID familyId, String currencyCode, UpdateFamilyCurrencyCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!FamilyCatalog.isValidCurrency(currencyCode)) {
            throw new InvalidFamilyRequestException(
                    "currencyCode", "Currency code must be one of ARS, USD, EUR, USDT");
        }
        if (command.preferredRateSource() != null
                && !FamilyCatalog.isValidRateSource(command.preferredRateSource())) {
            throw new InvalidFamilyRequestException(
                    "preferredRateSource",
                    "Preferred rate source must be one of USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA");
        }

        Family family = requireFamily(familyId);
        FamilyCurrency currency = familyCurrencyRepository
                .findByFamilyIdAndCurrencyCode(familyId, currencyCode)
                .orElseThrow(() -> new NotFoundException("Family currency not found"));

        if (Boolean.FALSE.equals(command.isActive())
                && currencyCode.equals(family.getBaseCurrency())) {
            throw new ConflictException("Base currency cannot be deactivated");
        }

        currency.update(command.isActive(), command.preferredRateSource());
        return toCurrencyView(familyCurrencyRepository.save(currency));
    }

    private void ensureBaseCurrencyEnabled(UUID familyId, String newBaseCurrency) {
        familyCurrencyRepository
                .findByFamilyIdAndCurrencyCode(familyId, newBaseCurrency)
                .ifPresentOrElse(
                        existing -> {
                            if (!existing.isActive()) {
                                existing.activate();
                                familyCurrencyRepository.save(existing);
                            }
                        },
                        () -> familyCurrencyRepository.save(
                                FamilyCurrency.create(familyId, newBaseCurrency, null)));
    }

    private static void validateSettingsCommand(UpdateFamilySettingsCommand command) {
        if (command.name() != null) {
            String trimmed = command.name().trim();
            if (trimmed.isEmpty() || trimmed.length() > FamilyCatalog.MAX_FAMILY_NAME_LENGTH) {
                throw new InvalidFamilyRequestException(
                        "name", "Family name must be between 1 and 120 characters");
            }
        }
        if (command.baseCurrency() != null
                && !FamilyCatalog.isValidCurrency(command.baseCurrency())) {
            throw new InvalidFamilyRequestException(
                    "baseCurrency", "Base currency must be one of ARS, USD, EUR, USDT");
        }
        if (command.referenceCurrency() != null
                && !FamilyCatalog.isValidReferenceCurrency(command.referenceCurrency())) {
            throw new InvalidFamilyRequestException(
                    "referenceCurrency", "Reference currency must be one of USD, EUR, UVA, ARS");
        }
        if (command.referenceRateSource() != null
                && !FamilyCatalog.isValidRateSource(command.referenceRateSource())) {
            throw new InvalidFamilyRequestException(
                    "referenceRateSource",
                    "Reference rate source must be one of USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA");
        }
        if (command.timezone() != null && !FamilyCatalog.isValidTimezone(command.timezone())) {
            throw new InvalidFamilyRequestException("timezone", "Timezone must be a valid ZoneId");
        }
    }

    private Family requireFamily(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        return familyRepository
                .findById(familyId)
                .orElseThrow(() -> new NotFoundException("Family not found"));
    }

    private FamilyView toFamilyView(Family family) {
        List<FamilyCurrencyView> enabledCurrencies = familyCurrencyRepository
                .findAllByFamilyIdAndActiveTrueOrderByCreatedAtAscCurrencyCodeAsc(family.getId())
                .stream()
                .map(FamilySettingsService::toCurrencyView)
                .toList();
        return new FamilyView(
                family.getId(),
                family.getName(),
                family.getBaseCurrency(),
                family.getReferenceCurrency(),
                family.getReferenceRateSource(),
                family.getTimezone(),
                enabledCurrencies);
    }

    private static FamilyCurrencyView toCurrencyView(FamilyCurrency currency) {
        return new FamilyCurrencyView(
                currency.getCurrencyCode(), currency.getPreferredRateSource(), currency.isActive());
    }
}
