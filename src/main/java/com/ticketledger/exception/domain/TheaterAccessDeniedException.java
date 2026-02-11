package com.ticketledger.exception.domain;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.exception.ApplicationException;

public class TheaterAccessDeniedException extends ApplicationException {

    public TheaterAccessDeniedException(String message) {
        super(message, ErrorCodeConstant.THEATER_ACCESS_DENIED, HttpStatus.FORBIDDEN, null);
    }

    public TheaterAccessDeniedException(String message, Map<String, Object> context) {
        super(message, ErrorCodeConstant.THEATER_ACCESS_DENIED, HttpStatus.FORBIDDEN, context);
    }
}
