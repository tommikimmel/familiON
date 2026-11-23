package com.familion.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecurityCodeCryptoTest {

    @Test
    void generateSixDigitCodePreservesLeadingZeros() {
        SecureRandom fixedZero = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 42;
            }
        };

        String code = SecurityCodeCrypto.generateSixDigitCode(fixedZero);
        assertThat(code).isEqualTo("000042");
        assertThat(code).matches("^\\d{6}$");
    }

    @Test
    void generateSecureTokenProduces32RandomBytesBase64UrlWithoutPadding() {
        String token = SecurityCodeCrypto.generateSecureToken();
        assertThat(token).doesNotContain("=");
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32);
    }

    @Test
    void sha256HexAndConstantTimeEqualsWorkAsExpected() {
        String hash = SecurityCodeCrypto.sha256Hex("123456");
        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(SecurityCodeCrypto.constantTimeEquals(hash, SecurityCodeCrypto.sha256Hex("123456")))
                .isTrue();
        assertThat(SecurityCodeCrypto.constantTimeEquals(hash, SecurityCodeCrypto.sha256Hex("654321")))
                .isFalse();
        assertThat(SecurityCodeCrypto.constantTimeEquals(null, hash)).isFalse();
    }
}
