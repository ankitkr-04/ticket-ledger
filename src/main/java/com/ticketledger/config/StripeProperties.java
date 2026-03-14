package com.ticketledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Stripe integration.
 * This class holds the necessary configuration values for Stripe, such as API
 * keys and webhook secrets.
 */

@ConfigurationProperties(prefix = "ticketledger.payment.stripe")
public record StripeProperties(
                String secretKey,
                Webhook webhook,
                String currency,
                String adminAlertEmail) {

        public record Webhook(
                        String secret) {
        }
}