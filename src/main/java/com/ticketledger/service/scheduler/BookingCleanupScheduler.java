package com.ticketledger.service.scheduler;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketledger.config.BookingProperties;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.service.BookingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupScheduler {
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final BookingProperties bookingProperties;

    @Scheduled(fixedDelayString = "${booking.cleanup-interval-ms:60000}") // every 60 seconds by default
    public void cleanupExpiredBookings() {
        var expiredBookings = bookingRepository.findByStatusAndLockedUntilBefore(
                BookingStatus.HELD,
                Instant.now(),
                PageRequest.of(0, 50));

        if (expiredBookings.isEmpty()) {
            log.debug("No expired bookings found for cleanup.");
            return;
        }

        for (var booking : expiredBookings) {
            try {
                bookingService.expireBooking(booking.getId());
            } catch (Exception e) {
                log.error("Failed to expire booking with ID: {}", booking.getId(), e);
            }
        }
    }
}
