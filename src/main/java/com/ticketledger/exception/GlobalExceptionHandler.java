package com.ticketledger.exception;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ticketledger.dto.ApiResponse;
import com.ticketledger.service.context.RequestContext;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final RequestContext requestContext;

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

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        "UNAUTHORIZED", // Code
                        ex.getMessage(), // Message (e.g., "Full authentication is required...")
                        requestContext.getRequestId(), // Request ID
                        null // Context (optional)
                ));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handleConcurrencyFailure(
            PessimisticLockingFailureException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CONCURRENCY_FAILURE",
                "The resource is currently locked. Please try again later.",
                null,
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

        String requestId = requestContext.getRequestId();

        ApiResponse<Object> response = ApiResponse.error(code, message, requestId, context);

        return new ResponseEntity<>(response, status);
    }
}
