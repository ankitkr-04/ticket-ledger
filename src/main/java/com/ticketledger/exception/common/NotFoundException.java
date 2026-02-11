package com.ticketledger.exception.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.ApplicationException;

public class NotFoundException extends ApplicationException {

    public NotFoundException(String message) {
        super(message, ErrorCodeConstant.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, null);
    }

    public NotFoundException(String message, Map<String, Object> context) {
        super(message, ErrorCodeConstant.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, context);
    }
}
