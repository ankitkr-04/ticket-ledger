package com.ticketledger.exception;

import java.util.Arrays;
import java.util.List;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    private final MeterRegistry meterRegistry;

    @Override
    public void handleUncaughtException(Throwable ex, java.lang.reflect.Method method, Object... params) {
        log.error("ASYNC FAILURE | Method: {} | Error: {} | Params: {}",
                method.getName(),
                ex.getMessage(),
                Arrays.toString(params),
                ex); // Exception as last arg ensures stack trace printing
        meterRegistry.counter("async.execution.failure",

                List.of(
                        Tag.of("method", method.getName()),
                        Tag.of("exception", ex.getClass().getSimpleName())))
                .increment();
    }
}
