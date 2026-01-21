package com.ticketledger.dto;

import java.math.BigDecimal;

/**
 * Normalized response from a refund operation.
 * 
 * @param providerRefundId The ID assigned to the refund by the provider (e.g.
 *                         re_12345)
 * @param status           The status of the refund (e.g. SUCCEEDED, PENDING,
 *                         FAILED)
 * @param amount           The amount refunded
 * @param providerResponse Raw JSON response from provider (for audit logs)
 */
public record RefundResponse(
        String providerRefundId,
        String status,
        BigDecimal amount,
        String providerResponse) {
}
