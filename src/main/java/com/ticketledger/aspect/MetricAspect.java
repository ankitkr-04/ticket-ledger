package com.ticketledger.aspect;

import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.ticketledger.annotation.BusinessMetric;
import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.TicketLedgerException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MetricAspect {
    private final MeterRegistry meterRegistry;

    @Around("@annotation(businessMetric)")
    public Object measureBusinessMetric(ProceedingJoinPoint joinPoint, BusinessMetric businessMetric) throws Throwable {

        Timer.Sample sample = Timer.start(meterRegistry);
        String metricName = businessMetric.name();

        String status = "success";
        String reason = "none";

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            status = "failure";
            reason = extractReason(ex);
            throw ex;
        } finally {
            recordMetric(sample, metricName, status, reason, businessMetric.recordLatency());
        }
    }

    private String extractReason(Throwable ex) {
        if (ex instanceof TicketLedgerException tle) {
            return tle.getErrorCode();
        }

        return ErrorCodeConstant.INTERNAL_ERROR;
    }

    private void recordMetric(Timer.Sample sample, String metricName, String status, String reason,
            boolean recordLatency) {
        meterRegistry.counter(metricName, List.of(
                Tag.of("status", status),
                Tag.of("reason", reason))).increment();
        if (recordLatency) {
            sample.stop(meterRegistry.timer(metricName + ".latency", List.of(
                    Tag.of("status", status))));
        }

    }
}
