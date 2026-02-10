package com.ticketledger.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ErrorMessageConstant {

    public static final String VALIDATION_FAILED = "Validation failed";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred";
    public static final String RESOURCE_LOCKED_RETRY = "The resource is currently locked. Please try again later.";
    public static final String ONLY_PAUSED_STATUS_SUPPORTED = "Only PAUSED status is currently supported via this endpoint";
    public static final String ACCESS_DENIED = "You don't have permission to access this resource";
    public static final String DATA_INTEGRITY_VIOLATION = "Database constraint violation";
    public static final String MALFORMED_REQUEST = "Invalid JSON format";
}
