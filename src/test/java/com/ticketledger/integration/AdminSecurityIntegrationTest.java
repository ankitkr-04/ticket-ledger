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

import com.ticketledger.domain.enums.ShowtimeStatus;
import com.ticketledger.dto.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    @Autowired
    private RestTestClient restClient;

    // IDs from seed_admin_security.sql
    private static final UUID THEATER_A_ID = UUID.fromString("01937b5c-2222-7000-8000-00000000000a");
    private static final UUID THEATER_B_ID = UUID.fromString("01937b5c-2222-7000-8000-00000000000b");

    private static final UUID ADMIN_A_ID = UUID.fromString("01937b5c-3333-7000-8000-00000000000a");
    private static final UUID ADMIN_B_ID = UUID.fromString("01937b5c-3333-7000-8000-00000000000b");

    private static final UUID SHOWTIME_A_ID = UUID.fromString("01937b5c-6666-7000-8000-00000000000a");
    private static final UUID SHOWTIME_B_ID = UUID.fromString("01937b5c-6666-7000-8000-00000000000b");

    private static final UUID BOOKING_B_ID = UUID.fromString("01937b5c-8888-7000-8000-999999999999");

    private String adminAToken;
    private String adminBToken;

    @BeforeEach
    void setup() {
        // Log in Admin A
        LoginRequest loginA = new LoginRequest("adminA@example.com", "SecurePass123"); // Password assumes hash in seed
                                                                                       // is valid or we register in
                                                                                       // test.

        // STRATEGY:
        // 1. Register Admin A & B via API (gets them in DB with valid password).
        // 2. BUT seed runs before. So seed will conflict or be overwritten?
        // Seed runs before. If I use seed, I need valid hash.
        // I will use a known hash in the seed script.
        // Hash for "password123": $2a$10$Dk.j.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1
        // (Fake).
        // Let's use a real one from a common generator or just Register them in test
        // and INSERT the access via SQL manually? No, test runs strictly separate.

        // BETTER: I will calculate a hash or trust the one I put if I can generate one.
        // For now, I will use a replacement strategy in the test: Register them, then
        // manually insert access? No, repositories are clean.

        // OK, I will put a PLACEHOLDER hash in seed and Update it in the test?
        // Or I can just register them and rely on a separate @Sql to grant access?
        // Let's try: Register via API -> then run SQL to grant access?
        // No, @Sql runs before.

        // I will use specific known hash for "password123":
        // $2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRryg0L.RzqlSheSFJmpzh.MGdy
        // (This is a valid bcrypt for "password123")
    }

    // Hash for "password123"
    // $2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRryg0L.RzqlSheSFJmpzh.MGdy

    @Test
    @Sql(scripts = "/sql/seed_admin_security.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void verifyStrictTheaterIsolation() {
        // 1. Login Admin A
        adminAToken = login("adminA@example.com", "password");

        // 2. Login Admin B
        adminBToken = login("adminB@example.com", "password");

        // ======================================================
        // SCENARIO 1: ATTACK - Admin A tries to Pause Theater B's Showtime
        // ======================================================
        UpdateShowtimeStatusRequest pauseRequest = new UpdateShowtimeStatusRequest(ShowtimeStatus.PAUSED,
                "Malicious Pause");

        restClient.patch()
                .uri("/api/v1/admin/showtimes/{id}/status", SHOWTIME_B_ID)
                .header("Authorization", "Bearer " + adminAToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(pauseRequest)
                .exchange()
                .expectStatus().isForbidden() // Expecting 403
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("THEATER_ACCESS_DENIED"); // Or FORBIDDEN

        // ======================================================
        // SCENARIO 2: LEAK - Admin A tries to Refund Theater B's Booking
        // ======================================================
        AdminRefundRequest refundRequest = new AdminRefundRequest("Malicious Refund");

        restClient.post()
                .uri("/api/v1/admin/bookings/{id}/refund", BOOKING_B_ID)
                .header("Authorization", "Bearer " + adminAToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(refundRequest)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("THEATER_ACCESS_DENIED");

        // ======================================================
        // SCENARIO 3: HAPPY PATH - Admin A Pauses Theater A's Showtime
        // ======================================================
        UpdateShowtimeStatusRequest validPauseRequest = new UpdateShowtimeStatusRequest(ShowtimeStatus.PAUSED,
                "Emergency Pause");

        restClient.patch()
                .uri("/api/v1/admin/showtimes/{id}/status", SHOWTIME_A_ID)
                .header("Authorization", "Bearer " + adminAToken) // Admin A owns Showtime A
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(validPauseRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.status").isEqualTo("PAUSED");
    }

    private String login(String email, String password) {
        LoginRequest loginRequest = new LoginRequest(email, password);
        ApiResponse<AuthResponse> response = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                })
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.data()).isNotNull();
        return response.data().accessToken();
    }
}
