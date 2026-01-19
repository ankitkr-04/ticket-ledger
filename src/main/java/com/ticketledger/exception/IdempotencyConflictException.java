package com.ticketledger.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends TicketLedgerException {
    public IdempotencyConflictException(String requestHash, String storedHash) {
        super(
                "Idempotency key reused with different payload",
                "IDEMPOTENCY_CONFLICT",
                HttpStatus.CONFLICT,
                Map.of("currentHash", requestHash, "storedHash", storedHash));
    }
}