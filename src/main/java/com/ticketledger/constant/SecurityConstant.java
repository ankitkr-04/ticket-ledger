package com.ticketledger.constant;

import java.util.UUID;

/**
 * Security-related constants including token prefixes and algorithms.
 * Centralizes crypto and authentication magic strings.
 */
public final class SecurityConstant {

    private SecurityConstant() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /**
     * Bearer token prefix used in Authorization headers.
     * Example: "Bearer " + accessToken
     */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * Length of Bearer prefix for substring operations.
     */
    public static final int BEARER_PREFIX_LENGTH = 7;

    /**
     * SHA-256 algorithm name for MessageDigest.
     * Used for request hash generation and validation.
     */
    public static final String HASH_ALGORITHM_SHA256 = "SHA-256";

    /**
     * Size in bytes for secure random token generation.
     * 32 bytes = 256 bits = sufficient entropy for refresh tokens.
     */
    public static final int SECURE_RANDOM_BYTES = 32;

    public static final UUID SYSTEM_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

}
