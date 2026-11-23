package com.familion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.auth.domain.EmailCodePurpose;
import com.familion.auth.domain.EmailSecurityCode;
import com.familion.auth.domain.SecurityCodeCrypto;
import com.familion.auth.infrastructure.persistence.EmailSecurityCodeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailCodeServiceTest {

    private EmailSecurityCodeRepository codeRepository;
    private AtomicReference<Instant> currentInstant;
    private EmailCodeService emailCodeService;

    @BeforeEach
    void setUp() {
        this.codeRepository = mock(EmailSecurityCodeRepository.class);
        this.currentInstant = new AtomicReference<>(Instant.parse("2026-06-01T12:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentInstant.get();
            }
        };
        this.emailCodeService = new EmailCodeService(codeRepository, clock);
        when(codeRepository.save(any(EmailSecurityCode.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void issueCodeConsumesPreviousPendingAndPersistsOnlySha256HashWithCorrectTtl() {
        UUID userId = UUID.randomUUID();

        String otp = emailCodeService.issueCode(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, "198.51.100.7");

        assertThat(otp).matches("^\\d{6}$");
        verify(codeRepository)
                .consumeAllPending(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, currentInstant.get());

        ArgumentCaptor<EmailSecurityCode> captor = ArgumentCaptor.forClass(EmailSecurityCode.class);
        verify(codeRepository).save(captor.capture());
        EmailSecurityCode saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPurpose()).isEqualTo(EmailCodePurpose.TWO_FACTOR_LOGIN);
        assertThat(saved.getCodeHash()).isEqualTo(SecurityCodeCrypto.sha256Hex(otp));
        assertThat(saved.getCodeHash()).isNotEqualTo(otp);
        assertThat(saved.getMaxAttempts()).isEqualTo((short) 5);
        assertThat(saved.getAttempts()).isEqualTo((short) 0);
        assertThat(saved.getExpiresAt()).isEqualTo(currentInstant.get().plus(Duration.ofMinutes(10)));
        assertThat(saved.getRequestIp()).isEqualTo("198.51.100.7");
    }

    @Test
    void issueCodeUsesExpectedFormatAndTtlForVerificationAndPasswordReset() {
        UUID userId = UUID.randomUUID();

        String verifyCode = emailCodeService.issueCode(userId, EmailCodePurpose.EMAIL_VERIFICATION, null);
        assertThat(verifyCode).matches("^\\d{6}$");

        String resetToken = emailCodeService.issueCode(userId, EmailCodePurpose.PASSWORD_RESET, "203.0.113.5");
        assertThat(Base64.getUrlDecoder().decode(resetToken)).hasSize(32);

        ArgumentCaptor<EmailSecurityCode> captor = ArgumentCaptor.forClass(EmailSecurityCode.class);
        verify(codeRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        EmailSecurityCode verifyEntity = captor.getAllValues().get(0);
        EmailSecurityCode resetEntity = captor.getAllValues().get(1);

        assertThat(verifyEntity.getExpiresAt()).isEqualTo(currentInstant.get().plus(Duration.ofHours(24)));
        assertThat(resetEntity.getExpiresAt()).isEqualTo(currentInstant.get().plus(Duration.ofMinutes(20)));
        assertThat(resetEntity.getCodeHash()).isEqualTo(SecurityCodeCrypto.sha256Hex(resetToken));
    }

    @Test
    void verifyCodeConsumesOnMatchAndRejectsExpiredCodes() {
        UUID userId = UUID.randomUUID();
        String raw = "007123";
        EmailSecurityCode stored = EmailSecurityCode.create(
                userId,
                EmailCodePurpose.EMAIL_VERIFICATION,
                SecurityCodeCrypto.sha256Hex(raw),
                (short) 5,
                currentInstant.get().plus(Duration.ofHours(24)),
                null);
        when(codeRepository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        userId, EmailCodePurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(stored));

        CodeVerificationResult ok =
                emailCodeService.verifyCode(userId, EmailCodePurpose.EMAIL_VERIFICATION, raw);
        assertThat(ok.valid()).isTrue();
        assertThat(stored.getConsumedAt()).isEqualTo(currentInstant.get());
        assertThat(stored.getAttempts()).isEqualTo((short) 1);

        // Expired code fails even with matching raw code
        EmailSecurityCode expired = EmailSecurityCode.create(
                userId,
                EmailCodePurpose.TWO_FACTOR_LOGIN,
                SecurityCodeCrypto.sha256Hex("654321"),
                (short) 5,
                currentInstant.get().plus(Duration.ofMinutes(10)),
                null);
        currentInstant.set(currentInstant.get().plus(Duration.ofMinutes(11)));
        when(codeRepository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        userId, EmailCodePurpose.TWO_FACTOR_LOGIN))
                .thenReturn(Optional.of(expired));

        CodeVerificationResult expiredResult =
                emailCodeService.verifyCode(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, "654321");
        assertThat(expiredResult.valid()).isFalse();
        assertThat(expiredResult.remainingAttempts()).isZero();
        assertThat(expired.getConsumedAt()).isNull();
    }

    @Test
    void verifyCodeTracksAttemptsAndBlocksAfterMaxAttemptsEvenWhenCorrectLater() {
        UUID userId = UUID.randomUUID();
        String raw = "042999";
        EmailSecurityCode stored = EmailSecurityCode.create(
                userId,
                EmailCodePurpose.TWO_FACTOR_LOGIN,
                SecurityCodeCrypto.sha256Hex(raw),
                (short) 5,
                currentInstant.get().plus(Duration.ofMinutes(10)),
                null);
        when(codeRepository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        userId, EmailCodePurpose.TWO_FACTOR_LOGIN))
                .thenReturn(Optional.of(stored));

        for (int attempt = 1; attempt <= 5; attempt++) {
            CodeVerificationResult res =
                    emailCodeService.verifyCode(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, "000000");
            assertThat(res.valid()).isFalse();
            assertThat(res.remainingAttempts()).isEqualTo(5 - attempt);
        }

        // 6th attempt with the right code is still rejected
        CodeVerificationResult sixth =
                emailCodeService.verifyCode(userId, EmailCodePurpose.TWO_FACTOR_LOGIN, raw);
        assertThat(sixth.valid()).isFalse();
        assertThat(sixth.remainingAttempts()).isZero();
        assertThat(stored.getConsumedAt()).isNull();
    }

    @Test
    void consumeResetTokenConsumesValidTokenAndRejectsExpired() {
        UUID userId = UUID.randomUUID();
        String token = SecurityCodeCrypto.generateSecureToken();
        String hash = SecurityCodeCrypto.sha256Hex(token);
        EmailSecurityCode stored = EmailSecurityCode.create(
                userId,
                EmailCodePurpose.PASSWORD_RESET,
                hash,
                (short) 5,
                currentInstant.get().plus(Duration.ofMinutes(20)),
                null);
        when(codeRepository.findFirstByPurposeAndCodeHashAndConsumedAtIsNullOrderByCreatedAtDesc(
                        EmailCodePurpose.PASSWORD_RESET, hash))
                .thenReturn(Optional.of(stored));

        Optional<UUID> resolved = emailCodeService.consumeResetToken(token);
        assertThat(resolved).contains(userId);
        assertThat(stored.getConsumedAt()).isEqualTo(currentInstant.get());

        // Second attempt on already-consumed code returns empty
        Optional<UUID> replay = emailCodeService.consumeResetToken(token);
        assertThat(replay).isEmpty();
    }
}
