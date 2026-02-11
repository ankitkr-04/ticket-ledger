package com.ticketledger.service.context;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;

@Component
@RequestScope
@RequiredArgsConstructor
public class RequestContext {

    private static final String GLOBAL_THEATER_TAG = "--global";

    private final ObjectProvider<Tracer> tracerProvider;
    private UUID theaterId;

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

    public UUID getTheaterId() {
        return theaterId;
    }

    public void setTheaterId(UUID theaterId) {
        this.theaterId = theaterId;
    }

    public String getTheaterTag() {
        return theaterId != null ? theaterId.toString() : GLOBAL_THEATER_TAG;
    }
}
