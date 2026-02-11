package com.ticketledger.filter;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ticketledger.service.context.BookingRequestContext;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TraceIdResponseFilter implements Filter {

    private final ObjectProvider<Tracer> tracerProvider;
    private final BookingRequestContext requestContext;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        Tracer tracer = tracerProvider.getIfAvailable();
        Span currentSpan = tracer != null ? tracer.currentSpan() : null;
        String traceId = currentSpan != null
                ? currentSpan.context().traceId()
                : requestContext.getRequestId();

        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("X-Request-ID", traceId);
        }

        chain.doFilter(request, response);
    }
}
