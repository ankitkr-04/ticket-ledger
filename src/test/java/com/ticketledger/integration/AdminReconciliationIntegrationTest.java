package com.ticketledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.ticketledger.domain.entity.AdminAuditLog;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.BookingSeat;
import com.ticketledger.domain.entity.Payment;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.entity.Showtime;
import com.ticketledger.domain.entity.User;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;
import com.ticketledger.domain.enums.BookingStatus;
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
import com.ticketledger.exception.PermanentGatewayException;
import com.ticketledger.service.gateway.PaymentGateway;
import com.ticketledger.service.scheduler.AdminReconciliationScheduler;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
class AdminReconciliationIntegrationTest {

        private static final UUID SEEDED_USER_ID = UUID.fromString("01937b5c-a666-7000-8000-666666666666");
        private static final UUID SEEDED_SHOWTIME_ID = UUID.fromString("01937b5c-a444-7000-8000-444444444444");
        private static final UUID SEEDED_SEAT_ID = UUID.fromString("01937b5c-a555-7000-8000-555555555551");

        @Autowired
        private AdminReconciliationScheduler scheduler;

        @Autowired
        private AdminAuditLogRepository adminAuditLogRepository;

        @Autowired
        private BookingRepository bookingRepository;

        @Autowired
        private BookingSeatRepository bookingSeatRepository;

        @Autowired
        private PaymentRepository paymentRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ShowtimeRepository showtimeRepository;

        @Autowired
        private SeatRepository seatRepository;

        @Autowired
        private ApplicationEvents applicationEvents;

        @MockitoBean
        private PaymentGateway paymentGateway;

        @Test
        @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void reconcile_stuckInitiatedLog_successfulGateway_shouldCompleteAndPublishEvent() {
                TestFixture fixture = createStuckRefundFixture();
                when(paymentGateway.refundPayment(anyString(), any(BigDecimal.class), anyString()))
                                .thenReturn(new RefundResponse("re_test_123", "SUCCEEDED",
                                                fixture.payment().getAmount(), "{}"));

                boolean processed = scheduler.processNextStuckJob(Instant.now().plusSeconds(5));

                assertThat(processed).isTrue();

                Booking updatedBooking = bookingRepository.findById(fixture.booking().getId()).orElseThrow();
                AdminAuditLog updatedLog = adminAuditLogRepository.findById(fixture.auditLog().getId()).orElseThrow();
                Seat updatedSeat = seatRepository.findById(fixture.seat().getId()).orElseThrow();

                assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.REFUNDED);
                assertThat(updatedLog.getStatus()).isEqualTo(AdminLogStatus.COMPLETED);
                assertThat(updatedLog.getProviderRefundId()).isEqualTo("re_test_123");
                assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

                long refundEvents = applicationEvents.stream(BookingRefundEvent.class).count();
                assertThat(refundEvents).isEqualTo(1);

                verify(paymentGateway, times(1))
                                .refundPayment(anyString(), any(BigDecimal.class), anyString());
        }

        @Test
        @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void reconcile_stuckInitiatedLog_permanentGatewayFailure_shouldMarkTerminalAndStopRetrying() {
                TestFixture fixture = createStuckRefundFixture();
                when(paymentGateway.refundPayment(anyString(), any(BigDecimal.class), anyString()))
                                .thenThrow(new PermanentGatewayException(
                                                "Invalid refund request",
                                                "PAYMENT_GATEWAY_PERMANENT_ERROR",
                                                HttpStatus.BAD_REQUEST));

                boolean firstRunProcessed = scheduler.processNextStuckJob(Instant.now().plusSeconds(5));
                boolean secondRunProcessed = scheduler.processNextStuckJob(Instant.now().plusSeconds(5));

                assertThat(firstRunProcessed).isTrue();
                assertThat(secondRunProcessed).isFalse();

                Booking updatedBooking = bookingRepository.findById(fixture.booking().getId()).orElseThrow();
                AdminAuditLog updatedLog = adminAuditLogRepository.findById(fixture.auditLog().getId()).orElseThrow();
                Seat updatedSeat = seatRepository.findById(fixture.seat().getId()).orElseThrow();

                assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.REFUND_FAILED);
                assertThat(updatedLog.getStatus()).isEqualTo(AdminLogStatus.PERMANENT_FAILURE);
                assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.SOLD);

                verify(paymentGateway, times(1))
                                .refundPayment(anyString(), any(BigDecimal.class), anyString());
        }

        @Test
        @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void reconcile_stuckInitiatedLog_transientFailure_shouldMarkFailedAndRevertBooking() {
                TestFixture fixture = createStuckRefundFixture();
                when(paymentGateway.refundPayment(anyString(), any(BigDecimal.class), anyString()))
                                .thenThrow(new RuntimeException("Gateway timeout"));

                boolean processed = scheduler.processNextStuckJob(Instant.now().plusSeconds(5));

                assertThat(processed).isTrue();

                Booking updatedBooking = bookingRepository.findById(fixture.booking().getId()).orElseThrow();
                AdminAuditLog updatedLog = adminAuditLogRepository.findById(fixture.auditLog().getId()).orElseThrow();
                Seat updatedSeat = seatRepository.findById(fixture.seat().getId()).orElseThrow();

                assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
                assertThat(updatedLog.getStatus()).isEqualTo(AdminLogStatus.FAILED);
                assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
        }

        private TestFixture createStuckRefundFixture() {
                User bookingUser = userRepository.findById(SEEDED_USER_ID).orElseThrow();
                Showtime showtime = showtimeRepository.findById(SEEDED_SHOWTIME_ID).orElseThrow();
                Seat seat = seatRepository.findById(SEEDED_SEAT_ID).orElseThrow();

                User adminUser = new User();
                adminUser.setEmail("admin-reconcile-" + UUID.randomUUID() + "@test.local");
                adminUser.setPasswordHash("irrelevant");
                adminUser.setRole(UserRole.ADMIN);
                adminUser.setVerified(true);
                adminUser = userRepository.save(adminUser);

                Booking booking = new Booking();
                booking.setUser(bookingUser);
                booking.setShowtime(showtime);
                booking.setStatus(BookingStatus.REFUND_INITIATED);
                booking = bookingRepository.save(booking);

                seat.setStatus(SeatStatus.SOLD);
                seat = seatRepository.save(seat);

                BookingSeat bookingSeat = new BookingSeat(booking, seat, BigDecimal.valueOf(15.00));
                bookingSeatRepository.save(bookingSeat);

                Payment payment = new Payment();
                payment.setBooking(booking);
                payment.setAmount(BigDecimal.valueOf(15.00));
                payment.setCurrency("USD");
                payment.setProvider(PaymentProvider.STRIPE);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setProviderTransactionId("txn_" + UUID.randomUUID());
                payment = paymentRepository.save(payment);

                AdminAuditLog auditLog = AdminAuditLog.builder()
                                .booking(booking)
                                .adminUser(adminUser)
                                .action(AdminLogAction.REFUND)
                                .status(AdminLogStatus.INITIATED)
                                .reason("Test stuck refund")
                                .idempotencyKey(UUID.randomUUID().toString().replace("-", ""))
                                .provider(PaymentProvider.STRIPE)
                                .build();
                auditLog = adminAuditLogRepository.save(auditLog);

                return new TestFixture(booking, payment, auditLog, seat);
        }

        private record TestFixture(Booking booking, Payment payment, AdminAuditLog auditLog, Seat seat) {
        }
}
