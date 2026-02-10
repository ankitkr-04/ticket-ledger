package com.ticketledger.service.gateway;

import java.math.BigDecimal;

import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.dto.RefundResponse;

/**
 * Interface for interacting with external payment gateways (Stripe, PayPal,
 * etc.).
 * <p>
 * Abstracts the complexity of provider-specific APIs.
 */
public interface PaymentGateway {

    /**
     * Refunds a payment through the external provider.
     * <p>
     * Must be idempotent.
     *
     * @param providerTransactionId the external transaction ID to refund (e.g.,
     *                              ch_12345)
     * @param amount                the amount to refund
     * @param idempotencyKey        unique key for safe retries
     * @return the refund details
     */
    RefundResponse refundPayment(String providerTransactionId, BigDecimal amount, String idempotencyKey);

    /**
     * Fetches the current status of a refund from the external provider.
     *
     * @param providerRefundId the provider's refund ID (optional if idempotencyKey
     *                         known)
     * @param idempotencyKey   the idempotency key used for the refund
     * @return the refund details
     */
    RefundResponse fetchRefundStatus(String providerRefundId, String idempotencyKey);

    PaymentStatus verifyPaymentStatus(String providerTransactionId);
}
