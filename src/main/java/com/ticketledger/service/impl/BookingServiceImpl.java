package com.ticketledger.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.domain.model.entity.*;
import com.ticketledger.domain.model.enums.BookingStatus;
import com.ticketledger.domain.model.enums.PaymentStatus;
import com.ticketledger.domain.model.enums.SeatStatus;
import com.ticketledger.domain.model.enums.ShowtimeStatus;
import com.ticketledger.domain.repository.*;
import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.dto.SeatDTO;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.SeatAlreadyBookedException;
import com.ticketledger.exception.ShowtimeNotFoundException;
import com.ticketledger.service.BookingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BookingSeatRepository bookingSeatRepository;

    private static final int LOCK_DURATION_MINUTES = 10;
    private static final BigDecimal DEFAULT_BASE_PRICE = new BigDecimal("10.00");
    private static final String CURRENCY_USD = "USD";

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse createBooking(CreateBookingRequest request, UUID userId) {

        // 1. Sort Seat IDs (CRITICAL: Deadlock Prevention)
        List<UUID> sortedSeatIds = request.seatIds().stream()
                .sorted()
                .toList();

        // 2. Acquire Locks (PESSIMISTIC_WRITE)
        List<Seat> lockedSeats = seatRepository.lockSeats(sortedSeatIds);

        // 3. Validate State (Check availability & mismatch)
        validateSeats(sortedSeatIds, lockedSeats, request.showtimeId());

        // 4. Validate Showtime (Fetch + Check)
        Showtime showtime = showtimeRepository.findById(request.showtimeId())
                .orElseThrow(() -> new ShowtimeNotFoundException(request.showtimeId()));

        validateShowtime(showtime);

        // 5. Fetch User
        User user = userRepository.findById(userId)
                // Use concrete BusinessException
                .orElseThrow(() -> new BusinessException(
                        "User not found",
                        "USER_NOT_FOUND",
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        Map.of("userId", userId)));

        // 6. Logic: Prepare Data
        Instant now = Instant.now();
        Instant expiresAt = now.plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES);

        // Create Booking Header
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setStatus(BookingStatus.HELD);
        booking.setLockedUntil(expiresAt);
        booking = bookingRepository.save(booking);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<BookingSeat> bookingSeats = new ArrayList<>();
        List<SeatDTO> seatDTOs = new ArrayList<>();

        // Process Seats
        for (Seat seat : lockedSeats) {
            seat.setStatus(SeatStatus.HELD);

            BigDecimal seatPrice = calculatePrice(seat);
            totalAmount = totalAmount.add(seatPrice);

            BookingSeat bookingSeat = new BookingSeat(booking, seat, seatPrice);
            bookingSeats.add(bookingSeat);

            seatDTOs.add(new SeatDTO(
                    seat.getId(),
                    seat.getSeatRow(),
                    seat.getSeatNumber(),
                    seat.getTier().getName(),
                    seatPrice,
                    SeatStatus.HELD.name()));
        }

        // Persist Changes
        seatRepository.saveAll(lockedSeats);
        bookingSeatRepository.saveAll(bookingSeats);

        // 7. Create Payment Intent
        Payment payment = createPendingPayment(booking, totalAmount);

        // 8. Map Response
        return buildResponse(booking, expiresAt, totalAmount, seatDTOs, payment);
    }

    // --- HELPER METHODS ---

    private BigDecimal calculatePrice(Seat seat) {
        return DEFAULT_BASE_PRICE.multiply(seat.getTier().getPriceMultiplier());
    }

    private Payment createPendingPayment(Booking booking, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setCurrency(CURRENCY_USD);
        payment.setProvider("STRIPE");
        payment.setStatus(PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    private void validateSeats(List<UUID> requestedIds, List<Seat> lockedSeats, UUID requestShowtimeId) {
        if (lockedSeats.size() != requestedIds.size()) {
            List<UUID> foundIds = lockedSeats.stream().map(Seat::getId).toList();
            List<UUID> missing = requestedIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            // Use Concrete Exception
            throw new BusinessException(
                    "Seats not found", "SEATS_NOT_FOUND",
                    org.springframework.http.HttpStatus.BAD_REQUEST, Map.of("missingIds", missing));
        }

        List<UUID> unavailableIds = lockedSeats.stream()
                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                .map(Seat::getId)
                .toList();

        if (!unavailableIds.isEmpty()) {
            throw new SeatAlreadyBookedException(unavailableIds);
        }

        boolean allMatchShowtime = lockedSeats.stream()
                .allMatch(s -> s.getShowtime().getId().equals(requestShowtimeId));

        if (!allMatchShowtime) {
            // Use Concrete Exception
            throw new BusinessException(
                    "Seats mismatch showtime", "INVALID_SHOWTIME_SEATS",
                    org.springframework.http.HttpStatus.BAD_REQUEST, Map.of("requestShowtimeId", requestShowtimeId));
        }
    }

    private void validateShowtime(Showtime showtime) {
        if (showtime.getStatus() != ShowtimeStatus.ACTIVE) {
            throw new BusinessException(
                    "Showtime not active", "SHOWTIME_CLOSED",
                    org.springframework.http.HttpStatus.BAD_REQUEST, Map.of("status", showtime.getStatus()));
        }
        if (showtime.getStartTime().isBefore(Instant.now())) {
            throw new BusinessException(
                    "Showtime started", "SHOWTIME_STARTED",
                    org.springframework.http.HttpStatus.BAD_REQUEST, Map.of("startTime", showtime.getStartTime()));
        }
    }

    private BookingResponse buildResponse(Booking booking, Instant expiresAt, BigDecimal total, List<SeatDTO> seats,
            Payment payment) {
        BookingResponse.PaymentDetails paymentDetails = new BookingResponse.PaymentDetails(
                payment.getId(),
                payment.getProvider(),
                payment.getStatus(),
                null,
                "sk_test_mock_secret_" + booking.getId(),
                "https://checkout.stripe.com/mock/" + booking.getId(),
                null,
                1);

        BookingResponse.AmountDetails amountDetails = new BookingResponse.AmountDetails(
                total,
                CURRENCY_USD,
                seats.stream().map(s -> new BookingResponse.SeatPriceBreakdown(s.seatId(), s.price())).toList());

        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                expiresAt,
                null,
                null,
                seats,
                amountDetails,
                paymentDetails,
                null);
    }
}