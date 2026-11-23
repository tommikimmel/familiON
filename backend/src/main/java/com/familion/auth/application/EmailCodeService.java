package com.familion.auth.application;

import com.familion.auth.domain.EmailCodePurpose;
import com.familion.auth.domain.EmailSecurityCode;
import com.familion.auth.domain.SecurityCodeCrypto;
import com.familion.auth.infrastructure.persistence.EmailSecurityCodeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailCodeService {

    private final EmailSecurityCodeRepository codeRepository;
    private final Clock clock;

    public EmailCodeService(EmailSecurityCodeRepository codeRepository, Clock clock) {
        this.codeRepository = codeRepository;
        this.clock = clock;
    }

    @Transactional
    public String issueCode(UUID userId, EmailCodePurpose purpose, String requestIp) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");

        Instant now = clock.instant();
        codeRepository.consumeAllPending(userId, purpose, now);

        String rawCode = purpose.isLongToken()
                ? SecurityCodeCrypto.generateSecureToken()
                : SecurityCodeCrypto.generateSixDigitCode();
        String codeHash = SecurityCodeCrypto.sha256Hex(rawCode);
        Instant expiresAt = now.plus(purpose.ttl());

        EmailSecurityCode entity = EmailSecurityCode.create(
                userId, purpose, codeHash, purpose.maxAttempts(), expiresAt, requestIp);
        codeRepository.save(entity);
        return rawCode;
    }

    @Transactional
    public CodeVerificationResult verifyCode(
            UUID userId, EmailCodePurpose purpose, String rawCode) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");

        Optional<EmailSecurityCode> maybeCode =
                codeRepository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        userId, purpose);
        if (maybeCode.isEmpty()) {
            return CodeVerificationResult.failed(0);
        }

        EmailSecurityCode code = maybeCode.get();
        Instant now = clock.instant();
        if (code.isConsumed() || code.isExpired(now) || code.hasExhaustedAttempts()) {
            return CodeVerificationResult.failed(0);
        }

        code.incrementAttempts();
        boolean matches = rawCode != null
                && !rawCode.isBlank()
                && SecurityCodeCrypto.constantTimeEquals(
                        code.getCodeHash(), SecurityCodeCrypto.sha256Hex(rawCode.trim()));

        if (!matches) {
            codeRepository.save(code);
            return CodeVerificationResult.failed(code.remainingAttempts());
        }

        code.consume(now);
        codeRepository.save(code);
        return CodeVerificationResult.matched();
    }

    @Transactional
    public Optional<UUID> consumeResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String candidateHash = SecurityCodeCrypto.sha256Hex(rawToken.trim());
        Optional<EmailSecurityCode> maybeCode =
                codeRepository.findFirstByPurposeAndCodeHashAndConsumedAtIsNullOrderByCreatedAtDesc(
                        EmailCodePurpose.PASSWORD_RESET, candidateHash);
        if (maybeCode.isEmpty()) {
            return Optional.empty();
        }

        EmailSecurityCode code = maybeCode.get();
        Instant now = clock.instant();
        if (code.isConsumed() || code.isExpired(now) || code.hasExhaustedAttempts()) {
            return Optional.empty();
        }

        code.incrementAttempts();
        if (!SecurityCodeCrypto.constantTimeEquals(code.getCodeHash(), candidateHash)) {
            codeRepository.save(code);
            return Optional.empty();
        }

        code.consume(now);
        codeRepository.save(code);
        return Optional.of(code.getUserId());
    }
}
