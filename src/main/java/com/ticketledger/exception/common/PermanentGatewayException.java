package com.ticketledger.exception.common;

import org.springframework.http.HttpStatus;

import com.ticketledger.exception.BusinessException;

/**
 * Signals a non-retryable payment gateway error.
 */
public class PermanentGatewayException extends BusinessException {

    public PermanentGatewayException(String message, String errorCode, HttpStatus status) {
        super(message, errorCode, status);
    }
}
