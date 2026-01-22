package com.ticketledger.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Email service abstraction for sending notifications.
 * Allows swapping implementations (Mock, SendGrid, AWS SES) without changing
 * event listeners.
 */
public interface EmailService {

    /**
     * Sends booking confirmation email to user.
     * 
     * @param bookingId the booking identifier
     * @param userEmail recipient email address
     * @param amount    total booking amount
     */
    void sendBookingConfirmation(UUID bookingId, String userEmail, BigDecimal amount);

    /**
     * Sends refunds notification email to user.
     * 
     * @param bookingId the booking identifier
     * @param userEmail recipient email address
     * @param amount    refund amount
     */
    void sendRefundNotification(UUID bookingId, String userEmail, BigDecimal amount);

    /**
     * Sends an admin alert email.
     *
     * @param recipientEmail the recipient email address
     * @param subject        the email subject
     * @param message        the email body
     */
    void sendAdminAlert(String recipientEmail, String subject, String message);
}
