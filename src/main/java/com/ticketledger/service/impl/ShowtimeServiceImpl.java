package com.ticketledger.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.entity.Showtime;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.enums.ShowtimeStatus;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.domain.repository.ShowtimeRepository;
import com.ticketledger.dto.ShowtimePauseResponse;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.NotFoundException;
import com.ticketledger.service.AdminAuditLogService;
import com.ticketledger.service.ShowtimeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShowtimeServiceImpl implements ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ShowtimePauseResponse pauseShowtime(UUID showtimeId, String reason, UUID adminId, String idempotencyKey) {
        log.info("Pausing showtime: {}", showtimeId);

        // 1. Lock Showtime
        Showtime showtime = showtimeRepository.findByIdWithLock(showtimeId)
                .orElseThrow(() -> new NotFoundException("Showtime not found: " + showtimeId));

        // 2. Already Paused Check
        if (showtime.getStatus() == ShowtimeStatus.PAUSED) {
            throw new BusinessException("Showtime is already paused", "SHOWTIME_ALREADY_PAUSED", HttpStatus.CONFLICT);
        }

        // 3. SAFETY GUARD: Prevent pausing if sold tickets exist (Phantom Show
        // Protection)
        boolean hasSoldTickets = bookingRepository.hasConfirmedBookings(showtimeId);
        if (hasSoldTickets) {
            throw new BusinessException(
                    "Cannot pause showtime with active sold tickets. Refund them first.",
                    "SHOWTIME_HAS_SOLD_TICKETS",
                    HttpStatus.CONFLICT);
        }

        // 4. Update Status
        showtime.setStatus(ShowtimeStatus.PAUSED);
        showtimeRepository.save(showtime);

        // 5. Lock & Expire HELD bookings (The Kill Switch)
        List<Booking> heldBookings = bookingRepository.findHeldBookingsByShowtimeIdWithLock(showtimeId);
        AtomicInteger seatsReleased = new AtomicInteger(0);

        for (Booking booking : heldBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);

            // Release Seats
            var bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
            bookingSeats.forEach(bs -> {
                Seat seat = bs.getSeat();
                seat.setStatus(SeatStatus.AVAILABLE);
                seatRepository.save(seat);
                seatsReleased.incrementAndGet();
            });
        }

        // 6. Audit Log
        adminAuditLogService.createShowtimeLog(
                showtimeId,
                adminId,
                AdminLogAction.PAUSE_SHOWTIME,
                reason,
                idempotencyKey);

        return new ShowtimePauseResponse(
                showtimeId,
                ShowtimeStatus.PAUSED.name(),
                heldBookings.size(),
                seatsReleased.get());
    }
}
