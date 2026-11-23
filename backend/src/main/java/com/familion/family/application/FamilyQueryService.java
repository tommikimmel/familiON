package com.familion.family.application;

import com.familion.common.error.NotFoundException;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.family.domain.Family;
import com.familion.family.domain.FamilyCurrency;
import com.familion.family.domain.User;
import com.familion.family.infrastructure.persistence.FamilyCurrencyRepository;
import com.familion.family.infrastructure.persistence.FamilyRepository;
import com.familion.family.infrastructure.persistence.UserRepository;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FamilyQueryService implements FamilyQueryApi {

    private final FamilyRepository familyRepository;
    private final FamilyCurrencyRepository familyCurrencyRepository;
    private final UserRepository userRepository;

    public FamilyQueryService(
            FamilyRepository familyRepository,
            FamilyCurrencyRepository familyCurrencyRepository,
            UserRepository userRepository) {
        this.familyRepository = familyRepository;
        this.familyCurrencyRepository = familyCurrencyRepository;
        this.userRepository = userRepository;
    }

    @Override
    public FamilySettings getSettings(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Family family = familyRepository
                .findById(familyId)
                .orElseThrow(() -> new NotFoundException("Family not found"));
        List<String> enabledCurrencies = familyCurrencyRepository
                .findAllByFamilyIdAndActiveTrueOrderByCreatedAtAscCurrencyCodeAsc(familyId)
                .stream()
                .map(FamilyCurrency::getCurrencyCode)
                .toList();
        return new FamilySettings(
                family.getId(),
                family.getName(),
                family.getBaseCurrency(),
                family.getReferenceCurrency(),
                family.getReferenceRateSource(),
                ZoneId.of(family.getTimezone()),
                enabledCurrencies);
    }

    @Override
    public MemberRef requireMember(UUID familyId, UUID userId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        User user = userRepository
                .findByIdAndFamilyIdAndActiveTrue(userId, familyId)
                .orElseThrow(() -> new NotFoundException("Family member not found"));
        return toMemberRef(user);
    }

    @Override
    public List<MemberRef> listMembers(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        return userRepository.findAllByFamilyIdAndActiveTrueOrderByCreatedAtAsc(familyId).stream()
                .map(FamilyQueryService::toMemberRef)
                .toList();
    }

    private static MemberRef toMemberRef(User user) {
        return new MemberRef(
                user.getId(),
                user.getFamilyId(),
                user.getFullName(),
                user.getRole(),
                user.getAccentColor());
    }
}
