package com.familion.family.application;

import com.familion.auth.application.api.SessionIssuer;
import com.familion.auth.application.api.SessionTokens;
import com.familion.common.error.ConflictException;
import com.familion.common.error.NotFoundException;
import com.familion.family.application.api.UserAccountApi;
import com.familion.family.domain.Family;
import com.familion.family.domain.FamilyInvitation;
import com.familion.family.domain.InvitationTokenCrypto;
import com.familion.family.domain.User;
import com.familion.family.infrastructure.config.FamilyAppProperties;
import com.familion.family.infrastructure.persistence.FamilyInvitationRepository;
import com.familion.family.infrastructure.persistence.FamilyRepository;
import com.familion.family.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    public static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final String INVITATION_NOT_FOUND_MESSAGE = "Invitation not found or expired";

    private final FamilyInvitationRepository invitationRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final UserAccountApi userAccountApi;
    private final SessionIssuer sessionIssuer;
    private final InvitationEmailSender emailSender;
    private final Clock clock;
    private final String publicBaseUrl;

    public InvitationService(
            FamilyInvitationRepository invitationRepository,
            FamilyRepository familyRepository,
            UserRepository userRepository,
            UserAccountApi userAccountApi,
            SessionIssuer sessionIssuer,
            InvitationEmailSender emailSender,
            Clock clock,
            FamilyAppProperties appProperties) {
        this.invitationRepository = invitationRepository;
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
        this.userAccountApi = userAccountApi;
        this.sessionIssuer = sessionIssuer;
        this.emailSender = emailSender;
        this.clock = clock;
        this.publicBaseUrl = stripTrailingSlash(
                Objects.requireNonNull(
                        appProperties.publicUrl(), "familion.app.public-url must not be null"));
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listPendingInvitations(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        List<FamilyInvitation> invitations = invitationRepository
                .findAllByFamilyIdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        familyId, clock.instant());
        if (invitations.isEmpty()) {
            return List.of();
        }
        Set<UUID> inviterIds = invitations.stream()
                .map(FamilyInvitation::getInvitedByUserId)
                .collect(Collectors.toSet());
        Map<UUID, User> invitersById = userRepository.findAllById(inviterIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return invitations.stream()
                .map(inv -> {
                    User inviter = invitersById.get(inv.getInvitedByUserId());
                    String inviterName = inviter != null ? inviter.getFullName() : "";
                    return new InvitationView(
                            inv.getId(),
                            inv.getEmail(),
                            inv.getRole(),
                            inv.getFamilyVisibility(),
                            inv.getExpiresAt(),
                            inv.getCreatedAt(),
                            new InviterView(inv.getInvitedByUserId(), inviterName));
                })
                .toList();
    }

    @Transactional
    public InvitationCreatedView createInvitation(
            UUID familyId, UUID invitedByUserId, CreateInvitationCommand command) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(invitedByUserId, "invitedByUserId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        String normalizedEmail = User.normalizeEmail(command.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        Family family = familyRepository
                .findById(familyId)
                .orElseThrow(() -> new NotFoundException("Family not found"));
        User inviter = userRepository
                .findByIdAndFamilyId(invitedByUserId, familyId)
                .orElseThrow(() -> new NotFoundException("Inviter not found"));

        String rawToken = InvitationTokenCrypto.generateRawToken();
        String tokenHash = InvitationTokenCrypto.sha256Hex(rawToken);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(INVITATION_TTL);

        FamilyInvitation invitation = FamilyInvitation.create(
                familyId,
                invitedByUserId,
                normalizedEmail,
                command.role(),
                command.familyVisibility(),
                tokenHash,
                now,
                expiresAt);
        FamilyInvitation saved = invitationRepository.save(invitation);

        String inviteUrl = publicBaseUrl + "/auth/invite?token=" + rawToken;
        emailSender.sendInvitation(
                saved.getEmail(), family.getName(), inviter.getFullName(), inviteUrl);

        return new InvitationCreatedView(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getFamilyVisibility(),
                saved.getExpiresAt(),
                saved.getCreatedAt(),
                new InviterView(inviter.getId(), inviter.getFullName()),
                inviteUrl);
    }

    @Transactional
    public void deleteInvitation(UUID familyId, UUID invitationId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(invitationId, "invitationId must not be null");

        FamilyInvitation invitation = invitationRepository
                .findByIdAndFamilyId(invitationId, familyId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        if (invitation.isAccepted()) {
            throw new ConflictException("Invitation has already been accepted");
        }
        invitationRepository.delete(invitation);
    }

    @Transactional(readOnly = true)
    public InvitationPreview previewInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new NotFoundException(INVITATION_NOT_FOUND_MESSAGE);
        }
        String tokenHash = InvitationTokenCrypto.sha256Hex(rawToken);
        FamilyInvitation invitation = invitationRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new NotFoundException(INVITATION_NOT_FOUND_MESSAGE));

        if (!invitation.isRedeemable(clock.instant())) {
            throw new NotFoundException(INVITATION_NOT_FOUND_MESSAGE);
        }

        Family family = familyRepository
                .findById(invitation.getFamilyId())
                .orElseThrow(() -> new NotFoundException(INVITATION_NOT_FOUND_MESSAGE));

        return new InvitationPreview(
                invitation.getEmail(),
                invitation.getRole(),
                family.getName(),
                invitation.getExpiresAt());
    }

    @Transactional
    public SessionTokens registerFromInvitation(RegisterInviteCommand command, String deviceInfo) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.token() == null || command.token().isBlank()) {
            throw new NotFoundException(INVITATION_NOT_FOUND_MESSAGE);
        }

        String tokenHash = InvitationTokenCrypto.sha256Hex(command.token());
        FamilyInvitation invitation = invitationRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new NotFoundException(INVITATION_NOT_FOUND_MESSAGE));

        Instant now = clock.instant();
        if (!invitation.isRedeemable(now)) {
            throw new NotFoundException(INVITATION_NOT_FOUND_MESSAGE);
        }

        UUID userId = userAccountApi.createMember(
                invitation.getFamilyId(),
                invitation.getEmail(),
                command.fullName(),
                command.password(),
                invitation.getRole(),
                invitation.getFamilyVisibility(),
                true);

        invitation.markAccepted(now);
        invitationRepository.save(invitation);

        return sessionIssuer.issueSession(userId, deviceInfo);
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
