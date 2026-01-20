package com.ticketledger.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.security.JwtService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public class ConcurrencyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    // IDs match src/test/resources/sql/seed_test_data.sql
    private final UUID SHOWTIME_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID SEAT_1_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private final UUID SEAT_2_ID = UUID.fromString("55555555-5555-5555-5555-555555555552");

    private String authToken;

    @BeforeEach
    void setUp() {
        // Generate a valid JWT for the seeded user (test@example.com)
        // Note: The user is inserted via SQL, so we just need a token that matches the
        // email.
        authToken = jwtService.generateToken("test@example.com");
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldPreventDoubleBooking_When5ConcurrentRequestsArrive() throws InterruptedException {

        // 1. Prepare Request: 5 Threads trying to book Seat 1 & Seat 2
        List<UUID> targetSeats = List.of(SEAT_1_ID, SEAT_2_ID);
        CreateBookingRequest requestBody = new CreateBookingRequest(SHOWTIME_ID, targetSeats);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 2. Fire Requests
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(authToken);
                    // Critical: Each thread acts as a unique request (Unique Idempotency Key)
                    // If we reused the key, we'd test Idempotency (replay), not Concurrency
                    // (locking).
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());

                    HttpEntity<CreateBookingRequest> entity = new HttpEntity<>(requestBody, headers);

                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "http://localhost:" + port + "/api/v1/bookings",
                            entity,
                            String.class);

                    // 3. Tally Results
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        System.out
                                .println("Unexpected Response: " + response.getStatusCode() + " " + response.getBody());
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 4. Wait for completion
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertThat(finished).as("Test timed out").isTrue();

        // 5. Verify Invariants
        // Exactly ONE request should succeed
        assertThat(successCount.get())
                .as("Exactly one booking should succeed")
                .isEqualTo(1);

        // Exactly FOUR requests should fail with 409 Conflict
        assertThat(conflictCount.get())
                .as("All other overlapping requests should fail with CONFLICT")
                .isEqualTo(4);

        // ZERO unexpected errors (500s, 400s)
        assertThat(errorCount.get())
                .as("There should be no unexpected errors")
                .isEqualTo(0);
    }
}