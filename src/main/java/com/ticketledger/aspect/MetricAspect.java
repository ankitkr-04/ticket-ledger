package com.ticketledger.aspect;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.ticketledger.annotation.BusinessMetric;
import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.TicketLedgerException;
import com.ticketledger.service.context.RequestContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class MetricAspect {
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILURE = "failure";
    private static final String REASON_NONE = "none";

    private final MeterRegistry meterRegistry;
    private final RequestContext requestContext;
    private final ThreadLocal<Deque<MetricInvocation>> invocationStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Before("@annotation(businessMetric)")
    public void startInvocation(BusinessMetric businessMetric) {
        Timer.Sample sample = businessMetric.recordLatency() ? Timer.start(meterRegistry) : null;
        invocationStack.get().push(new MetricInvocation(
                businessMetric.name(),
                businessMetric.recordLatency(),
                sample));
    }

    @AfterReturning("@annotation(businessMetric)")
    public void recordSuccess(BusinessMetric businessMetric) {
        recordOutcome(businessMetric.name(), STATUS_SUCCESS, REASON_NONE);
    }

    @AfterThrowing(pointcut = "@annotation(businessMetric)", throwing = "throwable")
    public void recordFailure(BusinessMetric businessMetric, Throwable throwable) {
        recordOutcome(businessMetric.name(), STATUS_FAILURE, extractReason(throwable));
    }

    private void recordOutcome(String fallbackMetricName, String status, String reason) {
        MetricInvocation invocation = popInvocation();

        String metricName = invocation != null ? invocation.metricName() : fallbackMetricName;
        String theaterTag = requestContext.getTheaterTag();

        meterRegistry.counter(metricName, List.of(
                Tag.of("status", status),
                Tag.of("reason", reason),
                Tag.of("theater_id", theaterTag))).increment();

        if (invocation != null && invocation.recordLatency() && invocation.sample() != null) {
            invocation.sample().stop(meterRegistry.timer(metricName + ".latency", List.of(
                    Tag.of("status", status),
                    Tag.of("theater_id", theaterTag))));
        }
    }

    private MetricInvocation popInvocation() {
        Deque<MetricInvocation> stack = invocationStack.get();
        if (stack.isEmpty()) {
            invocationStack.remove();
            return null;
        }

        MetricInvocation invocation = stack.pop();
        if (stack.isEmpty()) {
            invocationStack.remove();
        }
        return invocation;
    }

    private String extractReason(Throwable throwable) {
        if (throwable instanceof TicketLedgerException ticketLedgerException) {
            return ticketLedgerException.getErrorCode();
        }
        return ErrorCodeConstant.INTERNAL_ERROR;
    }

    private record MetricInvocation(
            String metricName,
            boolean recordLatency,
            Timer.Sample sample) {
    }
}
