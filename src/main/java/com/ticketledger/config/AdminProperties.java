package com.ticketledger.config;

import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for admin operations.
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        UUID systemUserId,
        Reconciliation reconciliation) {

    public record Reconciliation(
            int thresholdSeconds,
            int maxBatchSize) {
    }
}
