package com.ticketledger.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    boolean success,
    ErrorDetails error,
    Meta meta
) {
    public record ErrorDetails(
        String code,
        String message,
        String requestId,
        Map<String, Object> context
    ) {}

    public record Meta(
        Instant timestamp
    ) {}
}