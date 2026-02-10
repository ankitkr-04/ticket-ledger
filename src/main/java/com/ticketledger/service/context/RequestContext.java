package com.ticketledger.service.context;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RequestContext {

    private final ObjectProvider<Tracer> tracerProvider;

    public String getRequestId() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return UUID.randomUUID().toString();
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().traceId();
        }
        return UUID.randomUUID().toString();
    }
}
