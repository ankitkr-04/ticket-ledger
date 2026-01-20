package com.ticketledger.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import com.ticketledger.constant.SecurityConstant;

/**
 * Centralized cryptographic utilities.
 * Provides secure hashing and random token generation to eliminate crypto logic
 * from services.
 */
public final class CryptoUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates a SHA-256 hash of the input string.
     * 
     * @param input the string to hash
     * @return lowercase hex-encoded hash (64 characters)
     * @throws IllegalStateException if SHA-256 algorithm is not available
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SecurityConstant.HASH_ALGORITHM_SHA256);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a cryptographically secure opaque token using SecureRandom.
     * Suitable for refresh tokens, API keys, etc.
     * 
     * @return Base64 URL-safe encoded string (43 characters)
     */
    public static String generateOpaqueToken() {
        byte[] randomBytes = new byte[SecurityConstant.SECURE_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Converts byte array to lowercase hex string.
     * 
     * @param bytes the byte array to convert
     * @return hex-encoded string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
