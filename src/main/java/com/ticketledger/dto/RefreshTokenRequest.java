package com.ticketledger.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for rotating access tokens using a refresh token.
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}