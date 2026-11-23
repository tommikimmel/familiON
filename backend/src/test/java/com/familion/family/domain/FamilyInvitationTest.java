package com.familion.family.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FamilyInvitationTest {

    @Test
    void forcesFamilyFullVisibilityForAdultAndDefaultsToOwnOnlyForChild() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-06-08T10:00:00Z");

        FamilyInvitation adultInvite = FamilyInvitation.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "  Adult@Example.COM ",
                Role.ADULTO,
                FamilyVisibility.OWN_ONLY,
                InvitationTokenCrypto.sha256Hex("token-1"),
                now,
                expiresAt);
        assertThat(adultInvite.getEmail()).isEqualTo("adult@example.com");
        assertThat(adultInvite.getFamilyVisibility()).isEqualTo(FamilyVisibility.FAMILY_FULL);

        FamilyInvitation defaultChildInvite = FamilyInvitation.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "child1@example.com",
                Role.HIJO,
                null,
                InvitationTokenCrypto.sha256Hex("token-2"),
                now,
                expiresAt);
        assertThat(defaultChildInvite.getFamilyVisibility()).isEqualTo(FamilyVisibility.OWN_ONLY);

        FamilyInvitation summaryChildInvite = FamilyInvitation.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "child2@example.com",
                Role.HIJO,
                FamilyVisibility.FAMILY_SUMMARY,
                InvitationTokenCrypto.sha256Hex("token-3"),
                now,
                expiresAt);
        assertThat(summaryChildInvite.getFamilyVisibility())
                .isEqualTo(FamilyVisibility.FAMILY_SUMMARY);
    }

    @Test
    void evaluatesRedeemableBasedOnExpiryAndAcceptance() {
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-06-08T10:00:00Z");

        FamilyInvitation invitation = FamilyInvitation.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "invitee@example.com",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY,
                InvitationTokenCrypto.sha256Hex("token-4"),
                createdAt,
                expiresAt);

        assertThat(invitation.isRedeemable(Instant.parse("2026-06-05T10:00:00Z"))).isTrue();
        assertThat(invitation.isRedeemable(expiresAt)).isFalse();
        assertThat(invitation.isRedeemable(expiresAt.plusSeconds(1))).isFalse();

        invitation.markAccepted(Instant.parse("2026-06-02T10:00:00Z"));
        assertThat(invitation.isAccepted()).isTrue();
        assertThat(invitation.isRedeemable(Instant.parse("2026-06-03T10:00:00Z"))).isFalse();
    }

    @Test
    void generatesUrlSafeTokenAndDeterministicSha256HexHash() {
        String tokenA = InvitationTokenCrypto.generateRawToken();
        String tokenB = InvitationTokenCrypto.generateRawToken();

        assertThat(tokenA).isNotEqualTo(tokenB);
        assertThat(tokenA).hasSize(43).matches("^[A-Za-z0-9_-]+$");

        String hash = InvitationTokenCrypto.sha256Hex(tokenA);
        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(hash).isNotEqualTo(tokenA);
        assertThat(InvitationTokenCrypto.sha256Hex(tokenA)).isEqualTo(hash);
    }
}
