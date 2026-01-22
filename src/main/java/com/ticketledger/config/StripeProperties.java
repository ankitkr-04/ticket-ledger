package com.ticketledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration for Stripe.
 * Maps keys under "ticketledger.payment.stripe".
 */
@ConfigurationProperties(prefix = "ticketledger.payment.stripe")
public record StripeProperties(
    String secretKey,
    String adminAlertEmail
) {}
