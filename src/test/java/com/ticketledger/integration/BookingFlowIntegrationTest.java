package com.ticketledger.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.ticketledger.dto.*;

import tools.jackson.databind.json.JsonMapper;

/**
 * Integration test covering the complete booking flow:
 * Register → Login → Create Booking.
 * <p>
 * Uses real Spring context with Testcontainers PostgreSQL, RestTestClient, and
 * security.
 * Tests controller, service, and repository layers working together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BookingFlowIntegrationTest {

        @Autowired
        private RestTestClient restClient;

        @Autowired
        private JsonMapper objectMapper;

        // IDs from seed_test_data.sql
        private static final UUID SHOWTIME_ID = UUID.fromString("01937b5c-a444-7000-8000-444444444444");
        private static final UUID SEAT_1_ID = UUID.fromString("01937b5c-a555-7000-8000-555555555551");
        private static final UUID SEAT_2_ID = UUID.fromString("01937b5c-a555-7000-8000-555555555552");

        @Test
        @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void shouldCompleteFullBookingFlow_RegisterLoginAndBook() throws Exception {
                // ==================== STEP 1: REGISTER ====================
                String newUserEmail = "integrationtest@example.com";
                String password = "SecurePass123";

                RegisterRequest registerRequest = new RegisterRequest(newUserEmail, password, null, null, null);

                ApiResponse<AuthResponse> registerResponse = restClient.post()
                                .uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(registerRequest)
                                .exchange()
                                .expectStatus().isCreated()
                                .expectBody(new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                                })
                                .returnResult()
                                .getResponseBody();

                // Validate registration response
                assertThat(registerResponse).isNotNull();
                assertThat(registerResponse.success()).isTrue();
                assertThat(registerResponse.data()).isNotNull();
                assertThat(registerResponse.data().accessToken()).isNotBlank();
                assertThat(registerResponse.data().refreshToken()).isNotBlank();
                assertThat(registerResponse.data().expiresInMs()).isPositive();

                // ==================== STEP 2: LOGIN ====================
                LoginRequest loginRequest = new LoginRequest(newUserEmail, password);

                ApiResponse<AuthResponse> loginResponse = restClient.post()
                                .uri("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(loginRequest)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                                })
                                .returnResult()
                                .getResponseBody();

                // Validate login response
                assertThat(loginResponse).isNotNull();
                assertThat(loginResponse.success()).isTrue();
                assertThat(loginResponse.data()).isNotNull();

                String accessToken = loginResponse.data().accessToken();
                String refreshToken = loginResponse.data().refreshToken();

                assertThat(accessToken).isNotBlank();
                assertThat(refreshToken).isNotBlank();
                // Note: Tokens might be identical if generated at the same second (same iat
                // timestamp)
                // This is acceptable behavior - both are valid tokens

                // ==================== STEP 3: CREATE BOOKING ====================
                CreateBookingRequest bookingRequest = new CreateBookingRequest(
                                SHOWTIME_ID,
                                List.of(SEAT_1_ID, SEAT_2_ID));

                UUID idempotencyKey = UUID.randomUUID();

                ApiResponse<BookingResponse> bookingResponse = restClient.post()
                                .uri("/api/v1/bookings")
                                .header("Authorization", "Bearer " + accessToken)
                                .header("Idempotency-Key", idempotencyKey.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(bookingRequest)
                                .exchange()
                                .expectStatus().isCreated()
                                .expectBody(new org.springframework.core.ParameterizedTypeReference<ApiResponse<BookingResponse>>() {
                                })
                                .returnResult()
                                .getResponseBody();

                // Validate booking response
                assertThat(bookingResponse).isNotNull();
                assertThat(bookingResponse.success()).isTrue();
                assertThat(bookingResponse.data()).isNotNull();

                BookingResponse booking = bookingResponse.data();
                assertThat(booking.bookingId()).isNotNull();
                assertThat(booking.status().name()).isEqualTo("HELD");
                assertThat(booking.seats()).hasSize(2);

                // Validate payment details
                assertThat(booking.payment()).isNotNull();
                assertThat(booking.payment().status().name()).isEqualTo("PENDING");
                assertThat(booking.payment().provider().name()).isEqualTo("STRIPE");

                // Validate amount details
                assertThat(booking.amount()).isNotNull();
                assertThat(booking.amount().total()).isPositive();
                assertThat(booking.amount().currency()).isEqualTo("INR");
                // Retry with same idempotency key should return same booking
                ApiResponse<BookingResponse> idempotentResponse = restClient.post()
                                .uri("/api/v1/bookings")
                                .header("Authorization", "Bearer " + accessToken)
                                .header("Idempotency-Key", idempotencyKey.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(bookingRequest)
                                .exchange()
                                .expectStatus().isCreated()
                                .expectBody(new org.springframework.core.ParameterizedTypeReference<ApiResponse<BookingResponse>>() {
                                })
                                .returnResult()
                                .getResponseBody();

                assertThat(idempotentResponse).isNotNull();
                assertThat(idempotentResponse.data().bookingId())
                                .isEqualTo(booking.bookingId());

                // ==================== STEP 5: VERIFY SEAT LOCKING ====================
                // Attempt to book same seats should fail
                UUID newIdempotencyKey = UUID.randomUUID();

                restClient.post()
                                .uri("/api/v1/bookings")
                                .header("Authorization", "Bearer " + accessToken)
                                .header("Idempotency-Key", newIdempotencyKey.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(bookingRequest)
                                .exchange()
                                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.error.code").isEqualTo("SEAT_ALREADY_BOOKED");
        }

        @Test
        @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void shouldPreventUnauthorizedBooking_WithoutToken() {
                // Arrange
                CreateBookingRequest bookingRequest = new CreateBookingRequest(
                                SHOWTIME_ID,
                                List.of(SEAT_1_ID));

                // Act & Assert - Should return 401 Unauthorized
                restClient.post()
                                .uri("/api/v1/bookings")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(bookingRequest)
                                .exchange()
                                .expectStatus().isUnauthorized();
        }

        @Test
        @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void shouldRejectBooking_WithInvalidToken() {
                // Arrange
                CreateBookingRequest bookingRequest = new CreateBookingRequest(
                                SHOWTIME_ID,
                                List.of(SEAT_1_ID));

                // Act & Assert - Should return 401 Unauthorized with invalid token
                restClient.post()
                                .uri("/api/v1/bookings")
                                .header("Authorization", "Bearer invalid.jwt.token")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(bookingRequest)
                                .exchange()
                                .expectStatus().isUnauthorized();
        }

        @Test
        void shouldRejectRegistration_WhenEmailAlreadyExists() {
                // Arrange - First registration
                String email = "duplicate@example.com";
                RegisterRequest firstRequest = new RegisterRequest(email, "password123", null, null, null);

                restClient.post()
                                .uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(firstRequest)
                                .exchange()
                                .expectStatus().isCreated();

                // Act & Assert - Second registration with same email should fail
                RegisterRequest duplicateRequest = new RegisterRequest(email, "differentPassword", null, null, null);

                restClient.post()
                                .uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(duplicateRequest)
                                .exchange()
                                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.error.code").isEqualTo("EMAIL_ALREADY_EXISTS")
                                .jsonPath("$.error.message")
                                .value(message -> assertThat(message.toString())
                                                .containsIgnoringCase("email already in use"));
        }

        @Test
        void shouldRejectLogin_WithInvalidCredentials() {
                // Arrange - Register a user first
                String email = "validuser@example.com";
                String correctPassword = "CorrectPassword123";
                RegisterRequest registerRequest = new RegisterRequest(email, correctPassword, null, null, null);

                restClient.post()
                                .uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(registerRequest)
                                .exchange()
                                .expectStatus().isCreated();

                // Act & Assert - Try to login with wrong password
                LoginRequest wrongPasswordRequest = new LoginRequest(email, "WrongPassword");

                restClient.post()
                                .uri("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(wrongPasswordRequest)
                                .exchange()
                                .expectStatus().isUnauthorized();
        }

        @Test
        void shouldRefreshTokenSuccessfully() throws Exception {
                // Arrange - Register and get initial tokens
                String email = "refreshtest@example.com";
                RegisterRequest registerRequest = new RegisterRequest(email, "password123", null, null, null);

                ApiResponse<AuthResponse> registerResponse = restClient.post()
                                .uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(registerRequest)
                                .exchange()
                                .expectStatus().isCreated()
                                .expectBody(new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                                })
                                .returnResult()
                                .getResponseBody();

                String originalRefreshToken = registerResponse.data().refreshToken();
                String originalAccessToken = registerResponse.data().accessToken();

                // Wait 1 second to ensure different timestamp in JWT (iat claim)
                Thread.sleep(1000);

                // Act - Use refresh token
                RefreshTokenRequest refreshRequest = new RefreshTokenRequest(originalRefreshToken);

                ApiResponse<AuthResponse> refreshResponse = restClient.post()
                                .uri("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(refreshRequest)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                                })
                                .returnResult()
                                .getResponseBody();

                // Assert - Should get new tokens
                assertThat(refreshResponse).isNotNull();
                assertThat(refreshResponse.success()).isTrue();
                // Refresh token should always be different (token rotation)
                assertThat(refreshResponse.data().refreshToken()).isNotEqualTo(originalRefreshToken);
                // Access token may be same if issued in same second (JWT is deterministic)
                // But with our 1 second delay, it should be different
                assertThat(refreshResponse.data().accessToken()).isNotEqualTo(originalAccessToken);

                // Old refresh token should now be invalid (token rotation)
                restClient.post()
                                .uri("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new RefreshTokenRequest(originalRefreshToken))
                                .exchange()
                                .expectStatus().is5xxServerError(); // Should fail with revoked token
        }
}
