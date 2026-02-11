package com.ticketledger.constant;

/**
 * Constants for Stripe-specific error codes.
 * Replaces magic strings in StripePaymentGateway.
 */
public final class StripeErrorCode {

    private StripeErrorCode() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    public static final String CHARGE_ALREADY_REFUNDED = "charge_already_refunded";
    public static final String RESOURCE_MISSING = "resource_missing";
}
