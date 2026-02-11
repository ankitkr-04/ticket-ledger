package com.ticketledger.service.scheduler;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.config.AdminProperties;
import com.ticketledger.config.StripeProperties;
import com.ticketledger.domain.entity.AdminAuditLog;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.enums.AdminLogStatus;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.event.BookingRefundEvent;
import com.ticketledger.domain.repository.AdminAuditLogRepository;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.exception.ApplicationException;
import com.ticketledger.exception.common.PermanentGatewayException;
import com.ticketledger.service.EmailService;
import com.ticketledger.service.gateway.PaymentGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Cron job to reconcile 'stuck' admin operations.
 * <p>
 * TARGET: AdminAuditLog entries in 'INITIATED' state > 60 seconds old.
 * PROBLEM: DB said "Start", but we crashed before writing "Finished".
 * SOLUTION:
 * 1. Find 'INITIATED' logs.
 * 2. RE-ATTEMPT the refund call using the ORIGINAL Idempotency Key.
 * - Thanks to Stripe Idempotency, this is safe.
 * - If it already succeeded at Stripe, we get the same response back.
 * - If it never reached Stripe, we process it now.
 * 3. Update DB state to match reality.
 * 4. Alert the admin that the system had to auto-heal their action.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReconciliationScheduler {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PaymentRepository paymentRepository;
    private final SeatRepository seatRepository;
    private final PaymentGateway paymentGateway;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final StripeProperties stripeProperties;
    private final AdminProperties adminProperties;

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "AdminReconciliationScheduler_reconcilePayments")
    public void reconcileOrphanedRefunds() {
        Instant threshold = Instant.now().minusSeconds(adminProperties.reconciliation().thresholdSeconds());
        int processedCount = 0;
        int maxBatchSize = adminProperties.reconciliation().maxBatchSize();

        // Process one record at a time in separate transactions
        // Each iteration: Fetch 1 -> Process (Stripe call) -> Commit
        while (processedCount < maxBatchSize) {
            boolean recordProcessed = processNextStuckJob(threshold);
            if (!recordProcessed) {
                break; // No more stuck jobs found
            }
            processedCount++;
        }

        if (processedCount > 0) {
            log.info("✅ Reconciled {} stuck admin operations", processedCount);
        }
    }

    /**
     * Process ONE stuck job in a NEW transaction.
     * CRITICAL: This prevents holding DB connections during Stripe HTTP calls.
     * 
     * @return true if a job was processed, false if no jobs remain
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNextStuckJob(Instant threshold) {
        // Fetch ONE record with lock
        var stuckLogOpt = adminAuditLogRepository.findNextStuckJobWithLock(threshold);

        if (stuckLogOpt.isEmpty()) {
            return false; // No more stuck jobs
        }

        AdminAuditLog auditLog = stuckLogOpt.get();
        processStuckLog(auditLog);
        return true;
    }

    private void processStuckLog(AdminAuditLog auditLog) {
        log.info("Reconciling AdminAuditLog ID: {}", auditLog.getId());

        try {
            ReconciliationContext ctx = validateAndFetchContext(auditLog);
            RefundResponse response = executeRefund(ctx);
            completeRefund(ctx, response);
            log.info("✅ Reconciled ID {} as COMPLETED", auditLog.getId());
            
            alertAdmin(auditLog, "Refund Auto-Reconciled",
                    "The system successfully reconciled a stuck refund operation.\n" +
                            "Audit Log ID: " + auditLog.getId() + "\n" +
                            "Booking ID: " + ctx.booking.getId() + "\n" +
                            "Refund ID: " + response.providerRefundId());

        } catch (PermanentGatewayException e) {
            handlePermanentFailure(auditLog, e);
        } catch (ApplicationException | IllegalStateException e) {
            handleRetryableFailure(auditLog, e);
        } catch (Exception e) {
            handleUnknownFailure(auditLog, e);
        }
    }

    private record ReconciliationContext(AdminAuditLog auditLog, Booking booking, Payment payment) {}

    private ReconciliationContext validateAndFetchContext(AdminAuditLog auditLog) {
        if (auditLog.getBooking() == null) {
            throw new IllegalStateException("Skipping log ID " + auditLog.getId() + " - no booking target");
        }

        Booking booking = bookingRepository.findByIdWithLock(auditLog.getBooking().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No booking found for booking ID: " + auditLog.getBooking().getId()));

        Payment payment = paymentRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No payment found for booking ID: " + booking.getId()));

        if (auditLog.getIdempotencyKey() == null) {
            markAsFailed(auditLog, booking, "No idempotency key found for reconciliation");
             throw new IllegalStateException("No idempotency key found");
        }
        
        return new ReconciliationContext(auditLog, booking, payment);
    }

    private RefundResponse executeRefund(ReconciliationContext ctx) {
        return paymentGateway.refundPayment(
                ctx.payment.getProviderTransactionId(),
                ctx.payment.getAmount(),
                ctx.auditLog.getIdempotencyKey());
    }

    private void completeRefund(ReconciliationContext ctx, RefundResponse response) {
        completeRefund(ctx.auditLog, ctx.booking, response);
    }

    private void handlePermanentFailure(AdminAuditLog auditLog, Exception e) {
        log.error("Permanent error reconciling log ID: {}", auditLog.getId(), e);
        if (auditLog.getBooking() != null) {
            bookingRepository.findByIdWithLock(auditLog.getBooking().getId())
                    .ifPresent(booking -> markAsPermanentFailure(
                            auditLog, booking, "Permanent reconciliation failure: " + e.getMessage()));
        }
        alertAdmin(auditLog, "Refund Reconciliation Permanently Failed", 
                   "Manual intervention required.\nError: " + e.getMessage());
    }

    private void handleRetryableFailure(AdminAuditLog auditLog, Exception e) {
         log.error("Retryable/Business error reconciling log ID: {}", auditLog.getId(), e);
         // Mark as failed in audit log but Booking stays CONFIRMED (reverted state)
         if (auditLog.getBooking() != null) {
             bookingRepository.findByIdWithLock(auditLog.getBooking().getId())
                    .ifPresent(booking -> markAsFailed(auditLog, booking, "Reconciliation failed: " + e.getMessage()));
         }
         alertAdmin(auditLog, "Refund Reconciliation FAILED", 
                    "Manual intervention required.\nError: " + e.getMessage());
    }

    private void handleUnknownFailure(AdminAuditLog auditLog, Exception e) {
        log.error("Unknown error reconciling log ID: {}", auditLog.getId(), e);
        if (auditLog.getBooking() != null) {
             bookingRepository.findByIdWithLock(auditLog.getBooking().getId())
                    .ifPresent(booking -> markAsFailed(auditLog, booking, "Reconciliation failed: " + e.getMessage()));
        }
        alertAdmin(auditLog, "Refund Reconciliation FAILED", 
                   "Manual intervention required.\nError: " + e.getMessage());
    }

    /**
     * Complete the refund: Update booking, release seats, mark audit log as
     * completed.
     * This mirrors STEP 3 in BookingServiceImpl.processAdminRefund.
     */
    private void completeRefund(AdminAuditLog auditLog, Booking booking, RefundResponse response) {
        String originalReason = auditLog.getReason();

        // 1. Update Booking to REFUNDED
        booking.transitionTo(BookingStatus.REFUNDED);
        bookingRepository.save(booking);

        // 2. Release all seats back to AVAILABLE
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
        bookingSeats.forEach(bs -> {
            Seat seat = bs.getSeat();
            seat.setStatus(SeatStatus.AVAILABLE);
            seatRepository.save(seat);
        });

        // 3. Mark Audit Log as COMPLETED
        auditLog.markCompleted(auditLog.getProvider(), response.providerRefundId());
        auditLog.setReason("Reconciled by Scheduler: " + response.status());
        adminAuditLogRepository.save(auditLog);

        // 4. Publish refund event so user notification flow is triggered.
        eventPublisher.publishEvent(new BookingRefundEvent(
                booking.getId(),
                booking.getUser().getEmail(),
                response.amount(),
                originalReason != null ? originalReason : "Auto-reconciled by scheduler",
                Instant.now()));

        log.info("🎫 Released {} seats for booking {}", bookingSeats.size(), booking.getId());
    }

    /**
     * Mark refund as failed and revert booking state to CONFIRMED.
     */
    private void markAsFailed(AdminAuditLog auditLog, Booking booking, String reason) {
        // 1. Revert Booking to CONFIRMED (user can still use it)
        booking.transitionTo(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // 2. Mark Audit Log as FAILED
        auditLog.markFailed();
        auditLog.setReason(auditLog.getReason() + " | ERROR: " + reason);
        adminAuditLogRepository.save(auditLog);

        log.warn("⚠️ Reverted booking {} to CONFIRMED after reconciliation failure", booking.getId());
    }

    /**
     * Mark refund as permanently failed and move booking to REFUND_FAILED for
     * manual intervention.
     */
    private void markAsPermanentFailure(AdminAuditLog auditLog, Booking booking, String reason) {
        booking.transitionTo(BookingStatus.REFUND_FAILED);
        bookingRepository.save(booking);

        auditLog.setStatus(AdminLogStatus.PERMANENT_FAILURE);
        auditLog.setCompletedAt(Instant.now());
        auditLog.setReason(auditLog.getReason() + " | ERROR: " + reason);
        adminAuditLogRepository.save(auditLog);

        log.warn("⚠️ Marked audit log {} as PERMANENT_FAILURE and moved booking {} to REFUND_FAILED",
                auditLog.getId(), booking.getId());
    }

    private void alertAdmin(AdminAuditLog auditLog, String subject, String body) {
        String recipient = null;
        if (auditLog.getAdminUser() != null) {
            recipient = auditLog.getAdminUser().getEmail();
        }

        if (recipient == null || recipient.isEmpty()) {
            recipient = stripeProperties.adminAlertEmail();
        }

        emailService.sendAdminAlert(recipient, subject, body);
    }
}
