package com.ticketledger.exception.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.ApplicationException;

public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String message) {
        super(message, ErrorCodeConstant.ACCESS_DENIED, HttpStatus.FORBIDDEN, null);
    }

    public ForbiddenException(String message, Map<String, Object> context) {
        super(message, ErrorCodeConstant.ACCESS_DENIED, HttpStatus.FORBIDDEN, context);
    }
}
