package com.ticketledger.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.ticketledger.domain.entity.AdminAuditLog;
import com.ticketledger.domain.entity.Booking;
import com.ticketledger.domain.entity.Seat;
import com.ticketledger.domain.entity.Showtime;
import com.ticketledger.domain.enums.AdminLogAction;
import com.ticketledger.domain.enums.AdminLogStatus;
import com.ticketledger.domain.enums.BookingStatus;
import com.ticketledger.domain.enums.SeatStatus;
import com.ticketledger.domain.enums.ShowtimeStatus;
import com.ticketledger.domain.repository.AdminAuditLogRepository;
import com.ticketledger.domain.repository.BookingRepository;
import com.ticketledger.domain.repository.SeatRepository;
import com.ticketledger.domain.repository.ShowtimeRepository;
import com.ticketledger.dto.ApiResponse;
import com.ticketledger.dto.ShowtimePauseResponse;
import com.ticketledger.dto.UpdateShowtimeStatusRequest;
import com.ticketledger.security.JwtService;

import lombok.extern.slf4j.Slf4j;

/**
 * Integration Test for Admin Showtime Pause Operations.
 * 
 * Tests the "Phantom Show Protection" safety guard:
 * - Admins CAN pause showtimes with only HELD bookings
 * - Admins CANNOT pause showtimes with CONFIRMED/COMPLETED bookings
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Slf4j
class AdminShowtimeIntegrationTest {

        @Autowired
        private RestTestClient restClient;

        @Autowired
        private JwtService jwtService;

        @Autowired
        private ShowtimeRepository showtimeRepository;

        @Autowired
        private BookingRepository bookingRepository;

        @Autowired
        private SeatRepository seatRepository;

        @Autowired
        private AdminAuditLogRepository adminAuditLogRepository;

        // IDs from seed_showtime_pause_test.sql
        private static final UUID ADMIN_ID = UUID.fromString("01937b5c-9666-7000-8000-000000000001");
        private static final UUID SHOWTIME_WITH_HELD_ID = UUID.fromString("01937b5c-9444-7000-8000-000000000001");
        private static final UUID SHOWTIME_WITH_CONFIRMED_ID = UUID.fromString("01937b5c-9444-7000-8000-000000000002");

        private static final UUID HELD_BOOKING_1_ID = UUID.fromString("01937b5c-9888-7000-8000-000000000001");
        private static final UUID HELD_BOOKING_2_ID = UUID.fromString("01937b5c-9888-7000-8000-000000000002");
        private static final UUID CONFIRMED_BOOKING_ID = UUID.fromString("01937b5c-9888-7000-8000-000000000003");

        private static final UUID HELD_SEAT_1_ID = UUID.fromString("01937b5c-9555-7000-8000-000000000001");
        private static final UUID HELD_SEAT_2_ID = UUID.fromString("01937b5c-9555-7000-8000-000000000002");
        private static final UUID SOLD_SEAT_1_ID = UUID.fromString("01937b5c-9555-7000-8000-000000000003");
        private static final UUID SOLD_SEAT_2_ID = UUID.fromString("01937b5c-9555-7000-8000-000000000004");

        private String adminToken;

        @BeforeEach
        void setup() {
                // Generate JWT for admin user
                adminToken = jwtService.generateToken("admin@test.com");
        }

        /**
         * Scenario A: Admin pauses showtime with only HELD bookings.
         * Expected: Success - Showtime paused, bookings expired, seats released, audit
         * logged.
         */
        @Test
        @Sql(scripts = "/sql/seed_showtime_pause_test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void pauseShowtime_withHeldBookings_shouldSucceed() {
                // GIVEN: Showtime with 2 HELD bookings (from seed script)
                String idempotencyKey = "test-pause-held-" + UUID.randomUUID();
                UpdateShowtimeStatusRequest request = new UpdateShowtimeStatusRequest(
                                ShowtimeStatus.PAUSED,
                                "Test pause with HELD bookings");

                // WHEN: Admin pauses showtime
                ApiResponse<ShowtimePauseResponse> response = restClient.patch()
                                .uri("/api/v1/admin/showtimes/{id}/status", SHOWTIME_WITH_HELD_ID)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(request)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(
                                                new org.springframework.core.ParameterizedTypeReference<ApiResponse<ShowtimePauseResponse>>() {
                                                })
                                .returnResult()
                                .getResponseBody();

                // THEN: Verify response
                assertThat(response).isNotNull();
                assertThat(response.success()).isTrue();
                assertThat(response.data().showtimeId()).isEqualTo(SHOWTIME_WITH_HELD_ID);
                assertThat(response.data().status()).isEqualTo("PAUSED");
                assertThat(response.data().affectedBookings()).isEqualTo(2);
                assertThat(response.data().seatsReleased()).isEqualTo(2);

                // THEN: Verify showtime is PAUSED
                Showtime updatedShowtime = showtimeRepository.findById(SHOWTIME_WITH_HELD_ID).orElseThrow();
                assertThat(updatedShowtime.getStatus()).isEqualTo(ShowtimeStatus.PAUSED);

                // THEN: Verify bookings are EXPIRED
                Booking booking1 = bookingRepository.findById(HELD_BOOKING_1_ID).orElseThrow();
                Booking booking2 = bookingRepository.findById(HELD_BOOKING_2_ID).orElseThrow();
                assertThat(booking1.getStatus()).isEqualTo(BookingStatus.EXPIRED);
                assertThat(booking2.getStatus()).isEqualTo(BookingStatus.EXPIRED);

                // THEN: Verify seats are AVAILABLE
                Seat seat1 = seatRepository.findById(HELD_SEAT_1_ID).orElseThrow();
                Seat seat2 = seatRepository.findById(HELD_SEAT_2_ID).orElseThrow();
                assertThat(seat1.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
                assertThat(seat2.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

                // THEN: Verify audit log exists
                AdminAuditLog auditLog = adminAuditLogRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
                assertThat(auditLog.getAdminUser().getId()).isEqualTo(ADMIN_ID);
                assertThat(auditLog.getAction()).isEqualTo(AdminLogAction.PAUSE_SHOWTIME);
                assertThat(auditLog.getReason()).contains("Test pause with HELD bookings");
                assertThat(auditLog.getStatus()).isEqualTo(AdminLogStatus.COMPLETED);
                assertThat(auditLog.getShowtime().getId()).isEqualTo(SHOWTIME_WITH_HELD_ID);

                log.info("✅ Test passed: Showtime paused successfully with HELD bookings");
        }

        /**
         * Scenario B: Admin tries to pause showtime with CONFIRMED bookings.
         * Expected: Failure - 409 CONFLICT, showtime remains ACTIVE, booking remains
         * CONFIRMED.
         */
        @Test
        @Sql(scripts = "/sql/seed_showtime_pause_test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void pauseShowtime_withConfirmedBookings_shouldFail() {
                // GIVEN: Showtime with 1 CONFIRMED booking (sold tickets - from seed script)
                String idempotencyKey = "test-pause-fail-" + UUID.randomUUID();
                UpdateShowtimeStatusRequest request = new UpdateShowtimeStatusRequest(
                                ShowtimeStatus.PAUSED,
                                "Test pause with CONFIRMED bookings");

                // WHEN: Admin tries to pause showtime
                restClient.patch()
                                .uri("/api/v1/admin/showtimes/{id}/status", SHOWTIME_WITH_CONFIRMED_ID)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(request)
                                .exchange()
                                .expectStatus().isEqualTo(409) // CONFLICT
                                .expectBody()
                                .jsonPath("$.error.code").isEqualTo("SHOWTIME_HAS_SOLD_TICKETS")
                                .jsonPath("$.error.message").value(msg -> assertThat(msg.toString())
                                                .contains("Cannot pause showtime with active sold tickets"));

                // THEN: Verify showtime is still ACTIVE
                Showtime showtime = showtimeRepository.findById(SHOWTIME_WITH_CONFIRMED_ID).orElseThrow();
                assertThat(showtime.getStatus()).isEqualTo(ShowtimeStatus.ACTIVE);

                // THEN: Verify booking is still CONFIRMED
                Booking booking = bookingRepository.findById(CONFIRMED_BOOKING_ID).orElseThrow();
                assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

                // THEN: Verify seats are still SOLD
                Seat seat1 = seatRepository.findById(SOLD_SEAT_1_ID).orElseThrow();
                Seat seat2 = seatRepository.findById(SOLD_SEAT_2_ID).orElseThrow();
                assertThat(seat1.getStatus()).isEqualTo(SeatStatus.SOLD);
                assertThat(seat2.getStatus()).isEqualTo(SeatStatus.SOLD);

                // THEN: Verify NO audit log was created (operation failed before audit)
                var auditLog = adminAuditLogRepository.findByIdempotencyKey(idempotencyKey);
                assertThat(auditLog).isEmpty();

                log.info("✅ Test passed: Showtime pause correctly rejected with CONFIRMED bookings");
        }
}
