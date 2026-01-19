package com.ticketledger.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard response for successful authentication or token refresh.
 */
public record AuthResponse(
        @JsonProperty("access_token") String accessToken,

        @JsonProperty("refresh_token") String refreshToken,

        @JsonProperty("token_type") String tokenType,

        @JsonProperty("expires_in") long expiresInMs) {
    // Convenience constructor for standard Bearer tokens
    public AuthResponse(String accessToken, String refreshToken, long expiresInMs) {
        this(accessToken, refreshToken, "Bearer", expiresInMs);
    }
}