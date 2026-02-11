package com.ticketledger.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
                int lockDurationMinutes,
                BigDecimal defaultBasePrice,
                String currency,
                long cleanupIntervalMs,
                int cleanupSafetyBufferSeconds,
                int idempotencyExpirationHours) {
}