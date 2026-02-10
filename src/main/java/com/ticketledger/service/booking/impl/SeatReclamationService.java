package com.ticketledger.service.booking.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.config.BookingProperties;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.event.BookingConfirmedEvent;
import com.ticketledger.domain.event.BookingRefundEvent;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.exception.common.NotFoundException;
import com.ticketledger.service.AdminAuditLogService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatReclamationService {

    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingProperties bookingProperties;
    private final AdminAuditLogService adminAuditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void reclaimSeatForLatePayment(UUID lateBookingId) {
        List<UUID> seatIds = bookingSeatRepository.findSeatIdsByBookingId(lateBookingId);

        List<Seat> lockedSeats = seatRepository.findAllByIdInWithLock(seatIds);

        Booking user1Booking = bookingRepository.findByIdWithLock(lateBookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found", Map.of("bookingId", lateBookingId)));

        if (user1Booking.getStatus() == BookingStatus.CONFIRMED) {
            return;
        }

        List<Booking> conflictingBookings = new ArrayList<>(
                bookingSeatRepository.findConfirmedBookingsBySeatIds(seatIds).stream()
                        .filter(conflict -> !conflict.getId().equals(user1Booking.getId()))
                        .collect(
                                LinkedHashMap<UUID, Booking>::new,
                                (map, booking) -> map.putIfAbsent(booking.getId(), booking),
                                Map::putAll)
                        .values());

        if (conflictingBookings.isEmpty()) {
            processCleanReclaim(user1Booking, lockedSeats);
        } else {
            processReclamationBump(user1Booking, conflictingBookings, lockedSeats);
        }
    }

    private void processCleanReclaim(Booking user1Booking, List<Seat> lockedSeats) {
        lockedSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.SOLD);
            seatRepository.save(seat);
        });
        user1Booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(user1Booking);

        eventPublisher.publishEvent(new BookingConfirmedEvent(
                user1Booking.getId(),
                user1Booking.getUser().getEmail(),
                lockedSeats.stream().map(Seat::getTier)
                        .map(t -> bookingProperties.defaultBasePrice()
                                .multiply(t.getPriceMultiplier()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                Instant.now()));
    }

    private void processReclamationBump(Booking user1Booking, List<Booking> conflictingBookings,
            List<Seat> lockedSeats) {
        conflictingBookings.forEach(conflict -> {
            conflict.setStatus(BookingStatus.SYSTEM_CANCELLED);
            conflict.setBumpedByBookingId(user1Booking.getId());
            conflict.setSystemCancellationReason(
                    "Seat reclaimed by original payer after late payment verification.");
            bookingRepository.save(conflict);

            eventPublisher.publishEvent(new BookingRefundEvent(
                    conflict.getId(),
                    conflict.getUser().getEmail(),
                    BigDecimal.ZERO,
                    "Booking cancelled due to seat reclamation after late payment verification.",
                    Instant.now()));
        });

        user1Booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(user1Booking);

        lockedSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.SOLD);
            seatRepository.save(seat);
        });

        adminAuditLogService.logSystemAction(
                user1Booking.getId(),
                AdminLogAction.AUTO_RECLAMATION_CONFLICT,
                AdminLogStatus.COMPLETED,
                "Seats reclaimed by original payer after late payment verification, causing "
                        + conflictingBookings.size()
                        + " conflicting bookings to be cancelled.");
    }
}
