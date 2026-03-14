package com.ticketledger.exception.domain;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.ApplicationException;

public class InvalidWebhookSignatureException extends ApplicationException {

    public InvalidWebhookSignatureException() {
        super(
                "Webhook signature verification failed",
                ErrorCodeConstant.INVALID_WEBHOOK_SIGNATURE,
                HttpStatus.BAD_REQUEST,
                null);
    }
}