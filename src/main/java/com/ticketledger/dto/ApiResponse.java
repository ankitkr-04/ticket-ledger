package com.ticketledger.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetails error,
        Meta meta) {

    // --- STATIC FACTORIES (For Clean Usage) ---

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(
                true,
                data,
                null,
                new Meta(Instant.now(), requestId, null));
    }

    // Overload for pagination support later
    public static <T> ApiResponse<T> success(T data, Meta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> error(String code, String message, String requestId, Map<String, Object> context) {
        return new ApiResponse<>(
                false,
                null,
                new ErrorDetails(code, message, requestId, context),
                new Meta(Instant.now(), requestId, null));
    }

    // --- NESTED DTOs ---

    public record ErrorDetails(
            String code,
            String message,
            String requestId,
            Map<String, Object> context) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(
            Instant timestamp,
            String requestId,
            Pagination pagination // Nullable
    ) {
    }

    public record Pagination(
            int page,
            int size,
            boolean hasMore,
            Long totalElements) {
    }
}