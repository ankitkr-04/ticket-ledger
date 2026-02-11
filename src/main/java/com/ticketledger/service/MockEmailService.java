package com.ticketledger.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketledger.service.EmailService;

import lombok.extern.slf4j.Slf4j;

/**
 * Mock implementation of EmailService for development/testing.
 * Simulates SMTP latency with Thread.sleep and logs email details.
 * Production: Replace with SendGridEmailService or AwsSesEmailService.
 */
@Service
@Slf4j
public class MockEmailService implements EmailService {

    private static final int SMTP_LATENCY_MS = 1000;

    @Override
    public void sendBookingConfirmation(UUID bookingId, String userEmail, BigDecimal amount) {
        log.info("📧 [Mock Email] Preparing confirmation for Booking ID: {}", bookingId);

        try {
            // Simulate SMTP latency (non-blocking for the Virtual Thread)
            Thread.sleep(SMTP_LATENCY_MS);
            log.info("✅ [Mock Email] Sent to {} for amount ${}", userEmail, amount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ [Mock Email] Interrupted while sending email", e);
        }
    }

    @Override
    public void sendRefundNotification(UUID bookingId, String userEmail, BigDecimal amount) {
        log.warn("📧 [Email Service] Sending REFUND NOTICE for Booking ID: {}", bookingId);

        try {
            Thread.sleep(SMTP_LATENCY_MS);
            log.info("💸 [Email Sent] To: {}, Subject: 'Booking Failed - Refund Initiated'. Amount: ${}",
                    userEmail, amount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ [Mock Email] Interrupted while sending refund email", e);
        }
    }

    @Override
    public void sendAdminAlert(String recipientEmail, String subject, String message) {
        log.error("🚨 [ADMIN ALERT] To: {}, Subject: {}, Body: {}", recipientEmail, subject, message);
    }
}
