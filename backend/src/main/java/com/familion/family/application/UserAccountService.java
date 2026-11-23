package com.familion.family.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.api.RegisteredUser;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import com.familion.family.application.api.UserProfile;
import com.familion.family.application.api.UserProfileApi;
import com.familion.family.domain.Family;
import com.familion.family.domain.FamilyCurrency;
import com.familion.family.domain.ThemePreference;
import com.familion.family.domain.User;
import com.familion.family.infrastructure.persistence.FamilyCurrencyRepository;
import com.familion.family.infrastructure.persistence.FamilyRepository;
import com.familion.family.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService implements UserAccountApi, UserProfileApi {

    private final FamilyRepository familyRepository;
    private final FamilyCurrencyRepository familyCurrencyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserAccountService(
            FamilyRepository familyRepository,
            FamilyCurrencyRepository familyCurrencyRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.familyRepository = familyRepository;
        this.familyCurrencyRepository = familyCurrencyRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository
                .findByEmail(User.normalizeEmail(email))
                .map(UserAccountService::toUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccount require(UUID userId) {
        return toUserAccount(requireUser(userId));
    }

    @Override
    @Transactional
    public UUID createMember(
            UUID familyId,
            String email,
            String fullName,
            String rawPassword,
            Role role,
            FamilyVisibility visibility,
            boolean emailVerified) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        if (!familyRepository.existsById(familyId)) {
            throw new NotFoundException("Family not found");
        }
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }
        Instant verifiedAt = emailVerified ? clock.instant() : null;
        User user = User.createMember(
                familyId,
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                fullName.trim(),
                role,
                visibility,
                verifiedAt);
        return userRepository.save(user).getId();
    }

    @Override
    @Transactional
    public void markEmailVerified(UUID userId) {
        User user = requireUser(userId);
        user.markEmailVerified(clock.instant());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        User user = requireUser(userId);
        user.changePasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void setTwoFactorEnabled(UUID userId, boolean enabled) {
        User user = requireUser(userId);
        user.setTwoFactorEnabled(enabled);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public RegisteredUser registerFamilyWithAdult(
            String email, String rawPassword, String fullName, String familyName) {
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }
        Family family = familyRepository.save(Family.createDefault(familyName.trim()));
        familyCurrencyRepository.saveAll(List.of(
                FamilyCurrency.create(family.getId(), Family.DEFAULT_BASE_CURRENCY, null),
                FamilyCurrency.create(
                        family.getId(),
                        Family.DEFAULT_REFERENCE_CURRENCY,
                        Family.DEFAULT_REFERENCE_RATE_SOURCE)));

        User user = User.createMember(
                family.getId(),
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                fullName.trim(),
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                null);
        User saved = userRepository.save(user);
        return new RegisteredUser(saved.getId(), family.getId(), saved.getEmail(), saved.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return toUserProfile(requireUser(userId));
    }

    @Override
    @Transactional
    public UserProfile updatePreferences(UUID userId, String themePreference, String accentColor) {
        User user = requireUser(userId);
        ThemePreference parsedTheme = null;
        if (themePreference != null) {
            try {
                parsedTheme = ThemePreference.valueOf(themePreference);
            } catch (IllegalArgumentException ex) {
                throw new BusinessRuleException("Invalid theme preference: " + themePreference);
            }
        }
        user.updatePreferences(parsedTheme, accentColor);
        return toUserProfile(userRepository.save(user));
    }

    private User requireUser(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static UserAccount toUserAccount(User user) {
        return new UserAccount(
                user.getId(),
                user.getFamilyId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getFamilyVisibility(),
                user.getPasswordHash(),
                user.isEmailVerified(),
                user.isTwoFactorEnabled(),
                user.isActive());
    }

    private static UserProfile toUserProfile(User user) {
        return new UserProfile(
                user.getId(),
                user.getFamilyId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getFamilyVisibility(),
                user.getThemePreference().name(),
                user.getAccentColor(),
                user.isTwoFactorEnabled(),
                user.isEmailVerified());
    }
}
