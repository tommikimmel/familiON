package com.familion.family.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.family.application.EnableFamilyCurrencyCommand;
import com.familion.family.application.FamilySettingsService;
import com.familion.family.application.UpdateFamilyCurrencyCommand;
import com.familion.family.application.UpdateFamilySettingsCommand;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/family")
public class FamilyController {

    private final FamilySettingsService familySettingsService;
    private final CurrentUserProvider currentUserProvider;

    public FamilyController(
            FamilySettingsService familySettingsService, CurrentUserProvider currentUserProvider) {
        this.familySettingsService = familySettingsService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public FamilyResponse getFamily() {
        return FamilyResponse.from(
                familySettingsService.getFamily(currentUserProvider.get().familyId()));
    }

    @PatchMapping("/settings")
    @PreAuthorize("hasRole('ADULTO')")
    public FamilyResponse updateSettings(@Valid @RequestBody UpdateFamilySettingsRequest request) {
        return FamilyResponse.from(familySettingsService.updateSettings(
                currentUserProvider.get().familyId(),
                new UpdateFamilySettingsCommand(
                        request.name(),
                        request.baseCurrency(),
                        request.referenceCurrency(),
                        request.referenceRateSource(),
                        request.timezone())));
    }

    @GetMapping("/currencies")
    public List<FamilyCurrencyResponse> listCurrencies() {
        return familySettingsService
                .listCurrencies(currentUserProvider.get().familyId())
                .stream()
                .map(FamilyCurrencyResponse::from)
                .toList();
    }

    @PostMapping("/currencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO')")
    public FamilyCurrencyResponse enableCurrency(
            @Valid @RequestBody EnableFamilyCurrencyRequest request) {
        return FamilyCurrencyResponse.from(familySettingsService.enableCurrency(
                currentUserProvider.get().familyId(),
                new EnableFamilyCurrencyCommand(
                        request.currencyCode(), request.preferredRateSource())));
    }

    @PatchMapping("/currencies/{currencyCode}")
    @PreAuthorize("hasRole('ADULTO')")
    public FamilyCurrencyResponse updateCurrency(
            @PathVariable String currencyCode,
            @Valid @RequestBody UpdateFamilyCurrencyRequest request) {
        return FamilyCurrencyResponse.from(familySettingsService.updateCurrency(
                currentUserProvider.get().familyId(),
                currencyCode,
                new UpdateFamilyCurrencyCommand(
                        request.isActive(), request.preferredRateSource())));
    }
}
