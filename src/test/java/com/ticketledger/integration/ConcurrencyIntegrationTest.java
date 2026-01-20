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
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.ticketledger.dto.CreateBookingRequest;
import com.ticketledger.security.JwtService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public class ConcurrencyIntegrationTest {

    @Autowired
    private RestTestClient restClient;

    @Autowired
    private JwtService jwtService;

    // IDs match src/test/resources/sql/seed_test_data.sql
    private final UUID SHOWTIME_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID SEAT_1_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private final UUID SEAT_2_ID = UUID.fromString("55555555-5555-5555-5555-555555555552");

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = jwtService.generateToken("test@example.com");
    }

    @Test
    @Sql(scripts = "/sql/seed_test_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/clean_test_data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldPreventDoubleBooking_When5ConcurrentRequestsArrive() throws InterruptedException {

        List<UUID> targetSeats = List.of(SEAT_1_ID, SEAT_2_ID);
        CreateBookingRequest requestBody = new CreateBookingRequest(SHOWTIME_ID, targetSeats);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Use RestTestClient for the request
                    restClient.post()
                            .uri("/api/v1/bookings")
                            .header("Authorization", "Bearer " + authToken)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .exchange()
                            .expectBody()
                            .consumeWith(response -> {
                                int statusCode = response.getStatus().value();
                                HttpStatus status = HttpStatus.valueOf(statusCode);
                                if (status == HttpStatus.CREATED) {
                                    successCount.incrementAndGet();
                                } else if (status == HttpStatus.CONFLICT) {
                                    conflictCount.incrementAndGet();
                                } else {
                                    System.out.println("Unexpected: " + status);
                                    errorCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    e.printStackTrace();
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertThat(finished).as("Test timed out").isTrue();

        assertThat(successCount.get()).as("Only one booking should succeed").isEqualTo(1);
        assertThat(conflictCount.get()).as("Others should fail with CONFLICT").isEqualTo(4);
        assertThat(errorCount.get()).as("No unexpected errors").isEqualTo(0);
    }
}