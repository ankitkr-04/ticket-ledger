package com.ticketledger.constant;

/**
 * HTTP header names used throughout the application.
 * Centralizes magic strings to prevent typos and maintain consistency.
 */
public final class HttpHeaderConstant {

    private HttpHeaderConstant() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /**
     * Custom header for idempotency key in POST requests.
     * Example: "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000"
     */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /**
     * Standard authorization header for Bearer tokens.
     * Example: "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
     */
    public static final String AUTHORIZATION = "Authorization";
}
