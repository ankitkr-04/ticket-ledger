package com.ticketledger.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ErrorCodeConstant {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String API_ERROR = "API_ERROR";
    public static final String CONCURRENCY_FAILURE = "CONCURRENCY_FAILURE";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String MISSING_REQUEST_PARAMETER = "MISSING_REQUEST_PARAMETER";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String NOT_ACCEPTABLE = "NOT_ACCEPTABLE";

    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
    public static final String PAYMENT_GATEWAY_ERROR = "PAYMENT_GATEWAY_ERROR";
    public static final String PAYMENT_GATEWAY_PERMANENT_ERROR = "PAYMENT_GATEWAY_PERMANENT_ERROR";
    public static final String INVALID_PROVIDER_ID = "INVALID_PROVIDER_ID";
    public static final String MISSING_REFUND_ID = "MISSING_REFUND_ID";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String THEATER_ACCESS_DENIED = "THEATER_ACCESS_DENIED";
    public static final String SHOWTIME_NOT_FOUND = "SHOWTIME_NOT_FOUND";
    public static final String SHOWTIME_CLOSED = "SHOWTIME_CLOSED";
    public static final String SHOWTIME_STARTED = "SHOWTIME_STARTED";
    public static final String SEAT_ALREADY_BOOKED = "SEAT_ALREADY_BOOKED";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
}
