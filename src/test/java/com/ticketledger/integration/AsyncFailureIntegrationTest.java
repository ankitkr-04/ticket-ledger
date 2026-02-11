package com.ticketledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticketledger.config.AsyncConfig;
import com.ticketledger.exception.AsyncExceptionHandler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@SpringJUnitConfig(classes = { AsyncFailureIntegrationTest.AsyncTestConfig.class })
class AsyncFailureIntegrationTest {

    @Autowired
    private FailingService failingService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldTrapAsyncExceptionAndRecordMetric() {
        // 1. Trigger the failing async method
        failingService.throwErrorInBackground();

        // 2. Wait for the background thread to crash and the handler to catch it
        // (Since it's async, we can't assert immediately)
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            double failureCount = meterRegistry
                    .counter("async.execution.failure", "method", "throwErrorInBackground", "exception",
                            "RuntimeException")
                    .count();

            assertThat(failureCount).isEqualTo(1.0);
        });
    }

    /**
     * Inner config to create a dummy failing service just for this test.
     * We don't want to break the real NotificationService.
     */
    @Configuration
    @Import({ AsyncConfig.class, AsyncExceptionHandler.class })
    static class AsyncTestConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public FailingService failingService() {
            return new FailingService();
        }
    }

    @Service
    static class FailingService {
        @Async
        public void throwErrorInBackground() {
            throw new RuntimeException("I am a simulated failure!");
        }
    }
}
