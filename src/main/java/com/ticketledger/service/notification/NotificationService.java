package com.ticketledger.service.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ticketledger.domain.event.BookingConfirmedEvent;
import com.ticketledger.domain.event.BookingRefundEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationService {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        log.info("📧 [Mock Email] Preparing confirmation for Booking ID: {}", event.bookingId());

        try {
            // Simulate SMTP latency (non-blocking for the Virtual Thread)
            Thread.sleep(1000);
            log.info("✅ [Mock Email] Sent to {} for amount ${}", event.userEmail(), event.amount());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ [Mock Email] Interrupted while sending email", e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendRefundNotification(BookingRefundEvent event) {
        log.warn("📧 [Email Service] Sending REFUND NOTICE for Booking ID: {}", event.bookingId());
        try {
            Thread.sleep(1000); // Simulate SMTP
            log.info("💸 [Email Sent] To: {}, Subject: 'Booking Failed - Refund Initiated'. Amount: ${}",
                    event.userEmail(), event.amount());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
