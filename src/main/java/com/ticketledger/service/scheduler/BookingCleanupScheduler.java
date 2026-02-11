
package com.ticketledger.service.scheduler;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketledger.config.BookingProperties;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.service.booking.BookingExpirationService;
import com.ticketledger.service.booking.SeatReclamationService;
import com.ticketledger.service.gateway.PaymentGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupScheduler {
    private final BookingProperties bookingProperties;
    private final BookingExpirationService bookingExpirationService;
    private final SeatReclamationService seatReclamationService;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Scheduled(fixedDelayString = "${booking.cleanup-interval-ms:60000}")
    @SchedulerLock(name = "BookingCleanupScheduler_cleanupAndVerifyExpiredBookings", lockAtLeastFor = "${booking.cleanup.lock-at-least-for:PT30S}", lockAtMostFor = "${booking.cleanup.lock-at-most-for:PT10M}")
    public void cleanupAndVerifyExpiredBookings() {
        Instant threshold = Instant.now().minusSeconds(bookingProperties.cleanupSafetyBufferSeconds());
        int pageSize = 50;
        int totalProcessed = 0;

        // Process ALL pages — always re-query page 0 since processed bookings
        // change status and drop out of the result set.
        List<Booking> batch;
        do {
            batch = bookingRepository.findByStatusAndLockedUntilBefore(
                    BookingStatus.HELD, threshold, PageRequest.of(0, pageSize));
            for (Booking booking : batch) {
                try {
                    processCleanup(booking);
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Cleanup failed for booking {}: {}", booking.getId(), e.getMessage());
                }
            }
        } while (!batch.isEmpty());

        if (totalProcessed > 0) {
            log.info("Cleanup processed {} expired bookings", totalProcessed);
        }
    }

    private void processCleanup(Booking booking) {
        // Get the latest payment attempt
        var paymentOpt = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(booking.getId());

        // Path A: FAST PATH - No gateway transaction ever started
        if (paymentOpt.isEmpty() || paymentOpt.get().getProviderTransactionId() == null) {
            log.info("Fast-path expiring abandoned booking: {}", booking.getId());
            bookingExpirationService.expireBooking(booking.getId());
            return;
        }

        // Path B: VERIFICATION PATH - Check Stripe for late success
        String providerId = paymentOpt.get().getProviderTransactionId();
        PaymentStatus gatewayStatus = paymentGateway.verifyPaymentStatus(providerId);

        switch (gatewayStatus) {
            case SUCCESS -> {
                log.info("Late success detected for {}. Triggering reclamation (Bump).", booking.getId());
                seatReclamationService.reclaimOrBumpSeats(booking.getId());
            }
            case FAILED -> {
                log.info("Gateway confirmed failure for {}. Expiring hold.", booking.getId());
                bookingExpirationService.expireBooking(booking.getId());
            }
            case PENDING -> {
                // Do nothing. Wait for next run to give the user/webhook more time.
                log.debug("Payment for {} still pending at gateway. Skipping.", booking.getId());
            }

            default -> {
                log.warn("Unknown payment status {} for booking {}. Skipping.", gatewayStatus, booking.getId());
            }
        }
    }
}
