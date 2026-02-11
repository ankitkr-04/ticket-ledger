package com.ticketledger.dto;

import java.math.BigDecimal;

import com.ticketledger.domain.enums.GatewayStatus;

/**
 * Normalized response from a refund operation.
 * 
 * @param providerRefundId The ID assigned to the refund by the provider (e.g.
 *                         re_12345)
 * @param status           The status of the refund from the gateway
 * @param amount           The amount refunded
 * @param providerResponse Raw JSON response from provider (for audit logs)
 */
public record RefundResponse(
        String providerRefundId,
        GatewayStatus status,
        BigDecimal amount,
        String providerResponse) {
}
