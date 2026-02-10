package com.ticketledger.exception.domain;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.TicketLedgerException;

public class IdempotencyConflictException extends TicketLedgerException {
    public IdempotencyConflictException(String requestHash, String storedHash) {
        super(
                "Idempotency key reused with different payload",
                ErrorCodeConstant.IDEMPOTENCY_CONFLICT,
                HttpStatus.CONFLICT,
                Map.of("currentHash", requestHash, "storedHash", storedHash));
    }
}
