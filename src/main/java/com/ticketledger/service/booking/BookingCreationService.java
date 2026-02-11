package com.ticketledger.service.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketledger.annotation.BusinessMetric;
import com.ticketledger.config.BookingProperties;
import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.entity.Showtime;
import com.ticketledger.domain.entity.User;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.PaymentProvider;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.domain.repository.ShowtimeRepository;
import com.ticketledger.domain.repository.UserRepository;
import com.ticketledger.dto.BookingResponse;
import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.exception.BusinessException;
import com.ticketledger.exception.domain.SeatAlreadyBookedException;
import com.ticketledger.exception.domain.ShowtimeNotFoundException;
import com.ticketledger.service.IdempotencyService;
import com.ticketledger.service.context.BookingRequestContext;
import com.ticketledger.util.CryptoUtil;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class BookingCreationService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BookingSeatRepository bookingSeatRepository;

    private final BookingProperties bookingProperties;

    private final IdempotencyService idempotencyService;
    private final JsonMapper jsonMapper;
    private final BookingRequestContext requestContext;


    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 3)
    @BusinessMetric(name = "business.booking.attempt")
    public BookingResponse createBooking(CreateBookingRequest request, UUID userId, UUID idempotencyKey) {
        setTheaterContext(request.showtimeId());

        String requestHash = generateRequestHash(request, userId);
        if (!idempotencyService.lock(idempotencyKey, userId, requestHash)) {
            return handleIdempotencyHit(idempotencyKey);
        }

        List<Seat> lockedSeats = reserveSeats(request);
        Showtime showtime = verifyShowtime(request.showtimeId());
        User user = verifyUser(userId);

        Booking booking = createAndSaveBooking(user, showtime);
        List<BookingSeat> bookingSeats = createAndSaveBookingSeats(booking, lockedSeats);
        
        BigDecimal totalAmount = calculateTotalAmount(bookingSeats);
        Payment payment = createPendingPayment(booking, totalAmount);

        BookingResponse response = BookingResponse.fromEntity(booking, bookingSeats, payment);
        saveIdempotencyResponse(idempotencyKey, response);

        return response;
    }

    private void setTheaterContext(UUID showtimeId) {
         showtimeRepository.findTheaterIdById(showtimeId)
                 .ifPresent(requestContext::setTheaterId);
    }

    private List<Seat> reserveSeats(CreateBookingRequest request) {
        List<UUID> sortedSeatIds = request.seatIds().stream().sorted().toList();
        List<Seat> lockedSeats = seatRepository.lockSeats(sortedSeatIds);
        validateSeats(sortedSeatIds, lockedSeats, request.showtimeId());
        // Update status to HELD
        lockedSeats.forEach(seat -> seat.setStatus(SeatStatus.HELD));
        return seatRepository.saveAll(lockedSeats);
    }

    private Showtime verifyShowtime(UUID showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));
        
        // Context might depend on loaded showtime if not set earlier, but we set it from repo query
        requestContext.setTheaterId(showtime.getScreen().getTheater().getId());
        showtime.checkBookable();
        return showtime;
    }

    private User verifyUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "User not found",
                        "USER_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        Map.of("userId", userId)));
    }

    private Booking createAndSaveBooking(User user, Showtime showtime) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(bookingProperties.lockDurationMinutes(), ChronoUnit.MINUTES);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setStatus(BookingStatus.HELD);
        booking.setLockedUntil(expiresAt);
        return bookingRepository.save(booking);
    }

    private List<BookingSeat> createAndSaveBookingSeats(Booking booking, List<Seat> seats) {
        List<BookingSeat> bookingSeats = new ArrayList<>();
        for (Seat seat : seats) {
            BigDecimal seatPrice = calculatePrice(seat);
            BookingSeat bookingSeat = new BookingSeat(booking, seat, seatPrice);
            bookingSeats.add(bookingSeat);
        }
        return bookingSeatRepository.saveAll(bookingSeats);
    }

    private BigDecimal calculateTotalAmount(List<BookingSeat> bookingSeats) {
        return bookingSeats.stream()
                .map(BookingSeat::getPriceAtBooking)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void saveIdempotencyResponse(UUID idempotencyKey, BookingResponse response) {
        try {
            JsonNode requestJson = jsonMapper.valueToTree(response);
            idempotencyService.saveResponse(idempotencyKey, HttpStatus.CREATED.value(), requestJson);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Failed to serialize booking response",
                    ErrorCodeConstant.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String generateRequestHash(CreateBookingRequest request, UUID userId) {
        try {
            var combined = Map.of(
                    "userId", userId,
                    "request", request);
            String jsonString = jsonMapper.writeValueAsString(combined);
            return CryptoUtil.sha256(jsonString);
        } catch (JacksonException e) {
            throw new BusinessException(
                    "Failed to serialize request for hashing",
                    ErrorCodeConstant.REQUEST_HASH_SERIALIZATION_FAILURE,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("userId", userId));
        } catch (IllegalStateException e) {
            throw new BusinessException(
                    "Failed to generate request hash",
                    ErrorCodeConstant.REQUEST_HASH_GENERATION_FAILURE,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("userId", userId));
        }
    }

    private BookingResponse handleIdempotencyHit(UUID idempotencyKey) {
        var existingKeyOpt = idempotencyService.findKey(idempotencyKey);
        if (existingKeyOpt.isEmpty() || existingKeyOpt.get().getResponseStatus() == null
                || existingKeyOpt.get().getResponseBody() == null) {
            throw new BusinessException(
                    "Idempotent request in progress",
                    ErrorCodeConstant.IDEMPOTENCY_IN_PROGRESS,
                    HttpStatus.CONFLICT);
        }

        var existingKey = existingKeyOpt.get();
        try {
            return jsonMapper.treeToValue(existingKey.getResponseBody(), BookingResponse.class);
        } catch (JacksonException e) {
            throw new BusinessException(
                    "Failed to deserialize idempotent response",
                    ErrorCodeConstant.IDEMPOTENCY_RESPONSE_DESERIALIZATION_FAILURE,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("idempotencyKey", idempotencyKey));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Invalid idempotent response data",
                    ErrorCodeConstant.IDEMPOTENCY_RESPONSE_DESERIALIZATION_FAILURE,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("idempotencyKey", idempotencyKey));
        }
    }

    private BigDecimal calculatePrice(Seat seat) {
        return bookingProperties.defaultBasePrice()
                .multiply(seat.getTier().getPriceMultiplier())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Payment createPendingPayment(Booking booking, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setCurrency(bookingProperties.currency());
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setStatus(PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    private void validateSeats(List<UUID> requestedIds, List<Seat> lockedSeats, UUID requestShowtimeId) {
        if (lockedSeats.size() != requestedIds.size()) {
            List<UUID> foundIds = lockedSeats.stream().map(Seat::getId).toList();
            List<UUID> missing = requestedIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new BusinessException(
                    "Seats not found", "SEATS_NOT_FOUND",
                    HttpStatus.BAD_REQUEST, Map.of("missingIds", missing));
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
            throw new BusinessException(
                    "Seats mismatch showtime", "INVALID_SHOWTIME_SEATS",
                    HttpStatus.BAD_REQUEST,
                    Map.of("requestShowtimeId", requestShowtimeId));
        }
    }
}

