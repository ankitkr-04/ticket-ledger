package com.ticketledger.dto;

import com.ticketledger.domain.enums.ShowtimeStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateShowtimeStatusRequest(
        @NotNull(message = "Status is required") ShowtimeStatus status,
        @NotBlank(message = "Reason is required") String reason) {
}
