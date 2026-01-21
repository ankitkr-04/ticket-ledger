package com.ticketledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for processing an admin refund.
 *
 * @param reason Reason for the refund (required, 10-500 chars)
 */
public record AdminRefundRequest(
        @NotBlank(message = "Reason is required") @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters") String reason) {
}
