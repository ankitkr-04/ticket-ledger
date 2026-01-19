package com.ticketledger.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ticketledger.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // 1. Handle Domain Exceptions (Business Logic)
    @ExceptionHandler(TicketLedgerException.class)
    public ResponseEntity<Object> handleTicketLedgerException(TicketLedgerException ex, HttpServletRequest request) {
        log.warn("Business exception occurred: {}", ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), ex.getContext(), request);
    }

    // 2. Handle Validation Errors (@Valid failure)
    // We override the method from ResponseEntityExceptionHandler to keep JSON
    // format consistent
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Validation failed", errors, request);
    }

    // 3. Handle Catch-All (Unexpected 500s)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUncaughtException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null,
                request);
    }

    // 4. Handle Standard Spring MVC Exceptions (405, 415, etc.)
    // This ensures even framework errors return your JSON format
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return buildResponse((HttpStatus) status, "API_ERROR", ex.getMessage(), null, request);
    }

    // --- Helper ---

    private ResponseEntity<Object> buildResponse(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> context,
            Object requestSource) { // Accepts HttpServletRequest or WebRequest

        // Try to get the Trace ID from the request attribute (Standard in Spring Boot
        // 3+)
        // Or fallback to a new UUID if tracing is disabled.
        String requestId = getRequestId(requestSource);

        ApiResponse<Object> response = ApiResponse.error(code, message, requestId, context);

        return new ResponseEntity<>(response, status);
    }

    private String getRequestId(Object requestSource) {
        // Logic to extract correlation ID from MDC or Request Attributes
        // For now, generating a UUID is acceptable if Observability isn't set up yet
        return UUID.randomUUID().toString();
    }
}