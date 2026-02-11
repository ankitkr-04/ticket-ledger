package com.ticketledger.domain.enums;

/**
 * Represents the status returned by an external payment gateway (e.g., Stripe).
 * Replaces string constants previously in PaymentGatewayConstant.
 */
public enum GatewayStatus {
    SUCCEEDED,
    FAILED,
    PENDING,
    UNKNOWN;

    /**
     * Converts a raw gateway status string (e.g., from Stripe API) to enum.
     * Returns {@link #UNKNOWN} for unrecognized values.
     */
    public static GatewayStatus fromStripeStatus(String status) {
        if (status == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
