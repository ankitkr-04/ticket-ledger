package com.ticketledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.ticketledger.config.AdminProperties;
import com.ticketledger.domain.entity.AdminAuditLog;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.entity.Showtime;
import com.ticketledger.domain.entity.User;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.GatewayStatus;
import com.ticketledger.domain.enums.PaymentProvider;
import com.ticketledger.domain.enums.PaymentStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.enums.UserRole;
import com.ticketledger.domain.event.BookingRefundEvent;
import com.ticketledger.domain.repository.AdminAuditLogRepository;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.BookingSeatRepository;
import com.ticketledger.domain.repository.PaymentRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.domain.repository.ShowtimeRepository;
import com.ticketledger.domain.repository.UserRepository;
import com.ticketledger.dto.RefundResponse;
import com.ticketledger.service.booking.SeatReclamationService;
import com.ticketledger.service.gateway.PaymentGateway;
import com.ticketledger.service.scheduler.BookingCleanupScheduler;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
class BookingReclamationIntegrationTest {

    private static final UUID SEEDED_USER_ID = UUID.fromString("01937b5c-a666-7000-8000-666666666666");
    private static final UUID RECLAMATION_SHOWTIME_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID RECLAMATION_SEAT_1_ID = UUID.fromString("88888888-8888-8888-8888-888888888881");
    private static final UUID RECLAMATION_SEAT_2_ID = UUID.fromString("88888888-8888-8888-8888-888888888882");

    @Autowired
    private SeatReclamationService seatReclamationService;

    @Autowired
    private BookingCleanupScheduler bookingCleanupScheduler;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    private AdminProperties adminProperties;

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldReclaimSeatAndBumpOccupant_whenLatePaymentVerified() {
        var fixture = createReclamationConflictFixture();
        when(paymentGateway.refundPayment(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new RefundResponse(
                        "reclaim_ref_123",
                        GatewayStatus.SUCCEEDED,
                        fixture.user2Payment().getAmount(),
                        "{}"));

        seatReclamationService.reclaimOrBumpSeats(fixture.user1Booking().getId());

        Booking user1 = bookingRepository.findById(fixture.user1Booking().getId()).orElseThrow();
        Booking user2 = bookingRepository.findById(fixture.user2Booking().getId()).orElseThrow();
        Seat seat1 = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();
        Seat seat2 = seatRepository.findById(RECLAMATION_SEAT_2_ID).orElseThrow();

        assertThat(user1.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(user2.getStatus()).isEqualTo(BookingStatus.SYSTEM_CANCELLED);
        assertThat(user2.getDisplacedByBookingId()).isEqualTo(user1.getId());
        assertThat(user2.getSystemCancellationReason()).isNotBlank();
        assertThat(seat1.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(seat2.getStatus()).isEqualTo(SeatStatus.SOLD);

        AdminAuditLog conflictAudit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminLogAction.AUTO_RECLAMATION_CONFLICT)
                .findFirst()
                .orElseThrow();
        assertThat(conflictAudit.getAdminUser().getId()).isEqualTo(adminProperties.systemUserId());

        assertThat(applicationEvents.stream(BookingRefundEvent.class).count()).isGreaterThanOrEqualTo(1);

        Awaitility.await().untilAsserted(() -> {
            Payment bumpedPayment = paymentRepository.findById(fixture.user2Payment().getId()).orElseThrow();
            assertThat(bumpedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(bumpedPayment.getProviderRefundId()).isEqualTo("reclaim_ref_123");
        });
        verify(paymentGateway, atLeastOnce()).refundPayment(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldMarkBumpedBookingManualRefundRequired_whenListenerRefundFails() {
        var fixture = createReclamationConflictFixture();
        when(paymentGateway.refundPayment(anyString(), any(BigDecimal.class), anyString()))
                .thenThrow(new RuntimeException("Gateway down"));

        seatReclamationService.reclaimOrBumpSeats(fixture.user1Booking().getId());

        Awaitility.await().untilAsserted(() -> {
            Booking bumped = bookingRepository.findById(fixture.user2Booking().getId()).orElseThrow();
            assertThat(bumped.getStatus()).isEqualTo(BookingStatus.REFUND_REQUIRED_MANUAL);
        });

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<AdminAuditLog> failureLogs = auditLogRepository.findAll().stream()
                    .filter(log -> log.getAction() == AdminLogAction.AUTO_RECLAMATION_REFUND_FAILED)
                    .toList();
            assertThat(failureLogs).isNotEmpty();
            assertThat(failureLogs).anySatisfy(
                    log -> assertThat(log.getAdminUser().getId()).isEqualTo(adminProperties.systemUserId()));
        });
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void cleanup_shouldSkipHeldBookingsInsideThirtySecondSafetyBuffer() {
        Booking booking = createHeldBookingForCleanup(Instant.now().minusSeconds(10), true);

        bookingCleanupScheduler.cleanupAndVerifyExpiredBookings();

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(paymentGateway, never()).verifyPaymentStatus(anyString());
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void cleanup_shouldExpireAbandonedBookingWithoutGatewayVerification() {
        Booking booking = createHeldBookingForCleanup(Instant.now().minusSeconds(120), false);

        bookingCleanupScheduler.cleanupAndVerifyExpiredBookings();

        Booking refreshedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        Seat refreshedSeat = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();
        Payment refreshedPayment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        assertThat(refreshedBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(refreshedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(refreshedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentGateway, never()).verifyPaymentStatus(anyString());
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void cleanup_shouldReclaimWhenGatewayVerifiesSuccess() {
        Booking booking = createHeldBookingForCleanup(Instant.now().minusSeconds(120), true);
        when(paymentGateway.verifyPaymentStatus("pi_cleanup_pending")).thenReturn(PaymentStatus.SUCCESS);

        bookingCleanupScheduler.cleanupAndVerifyExpiredBookings();

        Booking refreshedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        Seat refreshedSeat = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();

        assertThat(refreshedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(refreshedSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
        verify(paymentGateway).verifyPaymentStatus("pi_cleanup_pending");
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void cleanup_shouldExpireWhenGatewayVerifiesFailure() {
        Booking booking = createHeldBookingForCleanup(Instant.now().minusSeconds(120), true);
        when(paymentGateway.verifyPaymentStatus("pi_cleanup_pending")).thenReturn(PaymentStatus.FAILED);

        bookingCleanupScheduler.cleanupAndVerifyExpiredBookings();

        Booking refreshedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        Seat refreshedSeat = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();
        Payment refreshedPayment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        assertThat(refreshedBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(refreshedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(refreshedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentGateway).verifyPaymentStatus("pi_cleanup_pending");
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void cleanup_shouldWaitWhenGatewayStillPending() {
        Booking booking = createHeldBookingForCleanup(Instant.now().minusSeconds(120), true);
        when(paymentGateway.verifyPaymentStatus("pi_cleanup_pending")).thenReturn(PaymentStatus.PENDING);

        bookingCleanupScheduler.cleanupAndVerifyExpiredBookings();

        Booking refreshedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        Seat refreshedSeat = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();
        Payment refreshedPayment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        assertThat(refreshedBooking.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(refreshedSeat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(refreshedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentGateway).verifyPaymentStatus("pi_cleanup_pending");
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldCancelAllConflictingOccupants_whenMultipleConfirmedBookingsExist() {
        var fixture = createReclamationConflictFixtureWithAdditionalOccupant();
        when(paymentGateway.refundPayment(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new RefundResponse(
                        "reclaim_ref_multi",
                        GatewayStatus.SUCCEEDED,
                        fixture.user2Payment().getAmount(),
                        "{}"));

        seatReclamationService.reclaimOrBumpSeats(fixture.user1Booking().getId());

        Booking user2 = bookingRepository.findById(fixture.user2Booking().getId()).orElseThrow();
        Booking user3 = bookingRepository.findById(fixture.user3Booking().getId()).orElseThrow();
        assertThat(user2.getStatus()).isEqualTo(BookingStatus.SYSTEM_CANCELLED);
        assertThat(user3.getStatus()).isEqualTo(BookingStatus.SYSTEM_CANCELLED);
        assertThat(user2.getDisplacedByBookingId()).isEqualTo(fixture.user1Booking().getId());
        assertThat(user3.getDisplacedByBookingId()).isEqualTo(fixture.user1Booking().getId());

        Awaitility.await().untilAsserted(() -> {
            Payment user2Payment = paymentRepository.findById(fixture.user2Payment().getId()).orElseThrow();
            Payment user3Payment = paymentRepository.findById(fixture.user3Payment().getId()).orElseThrow();
            assertThat(user2Payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(user3Payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        });

        AdminAuditLog conflictAudit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminLogAction.AUTO_RECLAMATION_CONFLICT)
                .findFirst()
                .orElseThrow();
        assertThat(conflictAudit.getReason()).contains("2 conflicting bookings");
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldBeRetrySafe_whenReclamationTriggeredTwiceForSameBooking() {
        var fixture = createReclamationConflictFixture();
        when(paymentGateway.refundPayment(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new RefundResponse(
                        "reclaim_ref_retry",
                        GatewayStatus.SUCCEEDED,
                        fixture.user2Payment().getAmount(),
                        "{}"));

        seatReclamationService.reclaimOrBumpSeats(fixture.user1Booking().getId());
        seatReclamationService.reclaimOrBumpSeats(fixture.user1Booking().getId());

        Awaitility.await().untilAsserted(() -> {
            Payment bumpedPayment = paymentRepository.findById(fixture.user2Payment().getId()).orElseThrow();
            assertThat(bumpedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        });
        verify(paymentGateway).refundPayment(anyString(), any(BigDecimal.class), anyString());
    }

    private ReclamationConflictFixture createReclamationConflictFixture() {
        User user1 = userRepository.findById(SEEDED_USER_ID).orElseThrow();
        User user2 = new User();
        user2.setEmail("user2-" + UUID.randomUUID() + "@test.local");
        user2.setPasswordHash("irrelevant");
        user2.setRole(UserRole.CUSTOMER);
        user2.setVerified(true);
        user2 = userRepository.save(user2);

        Showtime showtime = showtimeRepository.findById(RECLAMATION_SHOWTIME_ID).orElseThrow();
        Seat seat1 = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();
        Seat seat2 = seatRepository.findById(RECLAMATION_SEAT_2_ID).orElseThrow();

        Booking user1Booking = new Booking();
        user1Booking.setUser(user1);
        user1Booking.setShowtime(showtime);
        user1Booking.setStatus(BookingStatus.EXPIRED);
        user1Booking.setLockedUntil(Instant.now().minusSeconds(300));
        user1Booking = bookingRepository.save(user1Booking);

        bookingSeatRepository.saveAll(List.of(
                new BookingSeat(user1Booking, seat1, BigDecimal.valueOf(10)),
                new BookingSeat(user1Booking, seat2, BigDecimal.valueOf(10))));

        Payment user1Payment = new Payment();
        user1Payment.setBooking(user1Booking);
        user1Payment.setAmount(BigDecimal.valueOf(20));
        user1Payment.setCurrency("INR");
        user1Payment.setProvider(PaymentProvider.STRIPE);
        user1Payment.setStatus(PaymentStatus.SUCCESS);
        user1Payment.setProviderTransactionId("pi_late_success_u1");
        user1Payment = paymentRepository.save(user1Payment);

        Booking user2Booking = new Booking();
        user2Booking.setUser(user2);
        user2Booking.setShowtime(showtime);
        user2Booking.setStatus(BookingStatus.CONFIRMED);
        user2Booking = bookingRepository.save(user2Booking);

        bookingSeatRepository.saveAll(List.of(
                new BookingSeat(user2Booking, seat1, BigDecimal.valueOf(10)),
                new BookingSeat(user2Booking, seat2, BigDecimal.valueOf(10))));

        seat1.setStatus(SeatStatus.SOLD);
        seat2.setStatus(SeatStatus.SOLD);
        seatRepository.saveAll(List.of(seat1, seat2));

        Payment user2Payment = new Payment();
        user2Payment.setBooking(user2Booking);
        user2Payment.setAmount(BigDecimal.valueOf(20));
        user2Payment.setCurrency("INR");
        user2Payment.setProvider(PaymentProvider.STRIPE);
        user2Payment.setStatus(PaymentStatus.SUCCESS);
        user2Payment.setProviderTransactionId("pi_confirmed_u2");
        user2Payment = paymentRepository.save(user2Payment);

        return new ReclamationConflictFixture(user1Booking, user1Payment, user2Booking, user2Payment);
    }

    private MultiConflictFixture createReclamationConflictFixtureWithAdditionalOccupant() {
        ReclamationConflictFixture base = createReclamationConflictFixture();

        User user3 = new User();
        user3.setEmail("user3-" + UUID.randomUUID() + "@test.local");
        user3.setPasswordHash("irrelevant");
        user3.setRole(UserRole.CUSTOMER);
        user3.setVerified(true);
        user3 = userRepository.save(user3);

        Showtime showtime = showtimeRepository.findById(RECLAMATION_SHOWTIME_ID).orElseThrow();
        Seat seat1 = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();
        Seat seat2 = seatRepository.findById(RECLAMATION_SEAT_2_ID).orElseThrow();

        Booking user3Booking = new Booking();
        user3Booking.setUser(user3);
        user3Booking.setShowtime(showtime);
        user3Booking.setStatus(BookingStatus.CONFIRMED);
        user3Booking = bookingRepository.save(user3Booking);

        bookingSeatRepository.saveAll(List.of(
                new BookingSeat(user3Booking, seat1, BigDecimal.valueOf(10)),
                new BookingSeat(user3Booking, seat2, BigDecimal.valueOf(10))));

        Payment user3Payment = new Payment();
        user3Payment.setBooking(user3Booking);
        user3Payment.setAmount(BigDecimal.valueOf(20));
        user3Payment.setCurrency("INR");
        user3Payment.setProvider(PaymentProvider.STRIPE);
        user3Payment.setStatus(PaymentStatus.SUCCESS);
        user3Payment.setProviderTransactionId("pi_confirmed_u3");
        user3Payment = paymentRepository.save(user3Payment);

        return new MultiConflictFixture(base.user1Booking(), base.user2Booking(), base.user2Payment(), user3Booking,
                user3Payment);
    }

    private Booking createHeldBookingForCleanup(Instant lockedUntil, boolean withProviderTransactionId) {
        User user = userRepository.findById(SEEDED_USER_ID).orElseThrow();
        Showtime showtime = showtimeRepository.findById(RECLAMATION_SHOWTIME_ID).orElseThrow();
        Seat seat = seatRepository.findById(RECLAMATION_SEAT_1_ID).orElseThrow();

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setStatus(BookingStatus.HELD);
        booking.setLockedUntil(lockedUntil);
        booking = bookingRepository.save(booking);

        bookingSeatRepository.save(new BookingSeat(booking, seat, BigDecimal.valueOf(10)));

        seat.setStatus(SeatStatus.HELD);
        seatRepository.save(seat);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(BigDecimal.valueOf(10));
        payment.setCurrency("INR");
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setStatus(PaymentStatus.PENDING);
        if (withProviderTransactionId) {
            payment.setProviderTransactionId("pi_cleanup_pending");
        }
        paymentRepository.save(payment);

        return booking;
    }

    private record ReclamationConflictFixture(
            Booking user1Booking,
            Payment user1Payment,
            Booking user2Booking,
            Payment user2Payment) {
    }

    private record MultiConflictFixture(
            Booking user1Booking,
            Booking user2Booking,
            Payment user2Payment,
            Booking user3Booking,
            Payment user3Payment) {
    }
}
