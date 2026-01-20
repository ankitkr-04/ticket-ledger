package com.ticketledger.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CryptoUtil}.
 * Tests cryptographic utilities in isolation without Spring context.
 */
class CryptoUtilTest {

    @Test
    void sha256_ShouldReturnConsistentHash_ForSameInput() {
        // Arrange
        String input = "test@example.com";

        // Act
        String hash1 = CryptoUtil.sha256(input);
        String hash2 = CryptoUtil.sha256(input);

        // Assert
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex characters
    }

    @Test
    void sha256_ShouldReturnDifferentHashes_ForDifferentInputs() {
        // Arrange
        String input1 = "test1@example.com";
        String input2 = "test2@example.com";

        // Act
        String hash1 = CryptoUtil.sha256(input1);
        String hash2 = CryptoUtil.sha256(input2);

        // Assert
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void sha256_ShouldReturnLowercaseHex() {
        // Arrange
        String input = "test@example.com";

        // Act
        String hash = CryptoUtil.sha256(input);

        // Assert
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    @Test
    void sha256_ShouldHandleEmptyString() {
        // Arrange
        String input = "";

        // Act
        String hash = CryptoUtil.sha256(input);

        // Assert
        assertThat(hash).isNotEmpty();
        assertThat(hash).hasSize(64);
        // Empty string hash should be consistent
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void generateOpaqueToken_ShouldReturnBase64UrlSafeString() {
        // Act
        String token = CryptoUtil.generateOpaqueToken();

        // Assert
        assertThat(token).isNotEmpty();
        // Base64 URL-safe without padding should match pattern
        assertThat(token).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void generateOpaqueToken_ShouldReturnUniqueTokens() {
        // Act
        String token1 = CryptoUtil.generateOpaqueToken();
        String token2 = CryptoUtil.generateOpaqueToken();
        String token3 = CryptoUtil.generateOpaqueToken();

        // Assert - All tokens should be unique
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token2).isNotEqualTo(token3);
        assertThat(token1).isNotEqualTo(token3);
    }

    @Test
    void generateOpaqueToken_ShouldReturn43Characters() {
        // Act
        String token = CryptoUtil.generateOpaqueToken();

        // Assert
        // 32 bytes encoded in Base64 URL-safe without padding = 43 characters
        assertThat(token).hasSize(43);
    }

    @Test
    void generateOpaqueToken_ShouldBeDecodableAsBase64() {
        // Act
        String token = CryptoUtil.generateOpaqueToken();

        // Assert - Should be valid Base64 URL-safe encoding
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32); // Should decode to 32 bytes
    }

    @Test
    void constructor_ShouldThrowException_WhenInstantiated() {
        // Assert
        assertThatThrownBy(() -> {
            // Use reflection to invoke private constructor
            var constructor = CryptoUtil.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
