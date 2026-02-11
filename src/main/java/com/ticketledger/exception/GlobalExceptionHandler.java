package com.ticketledger.exception;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ticketledger.constant.ErrorCodeConstant;
import com.ticketledger.constant.ErrorMessageConstant;
import com.ticketledger.dto.ApiResponse;
import com.ticketledger.service.context.BookingRequestContext;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final BookingRequestContext requestContext;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplicationException(ApplicationException ex, HttpServletRequest request) {
        log.warn("Business exception occurred: {}", ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), ex.getContext());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCodeConstant.INVALID_REQUEST,
                ErrorMessageConstant.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ErrorCodeConstant.ACCESS_DENIED,
                ErrorMessageConstant.ACCESS_DENIED, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return buildResponse(HttpStatus.CONFLICT, ErrorCodeConstant.DATA_INTEGRITY_VIOLATION,
                ErrorMessageConstant.DATA_INTEGRITY_VIOLATION, Map.of("detail", detail));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCodeConstant.MALFORMED_REQUEST,
                ErrorMessageConstant.MALFORMED_REQUEST, null);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCodeConstant.MISSING_REQUEST_PARAMETER,
                ex.getMessage(), Map.of("parameterName", ex.getParameterName(), "parameterType", ex.getParameterType()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUncaughtException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getClass().getName(), ex);
        Map<String, Object> context = Map.of(
                "exceptionType", ex.getClass().getSimpleName(),
                "exceptionMessage", ex.getMessage() == null ? "" : ex.getMessage());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeConstant.INTERNAL_ERROR,
                ErrorMessageConstant.UNEXPECTED_ERROR, context);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        ErrorCodeConstant.UNAUTHORIZED,
                        ex.getMessage(),
                        requestContext.getRequestId(),
                        null
                ));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handleConcurrencyFailure(
            PessimisticLockingFailureException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodeConstant.CONCURRENCY_FAILURE,
                ErrorMessageConstant.RESOURCE_LOCKED_RETRY, null);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return buildResponse(httpStatus, resolveErrorCode(httpStatus), ex.getMessage(), null);
    }

    private ResponseEntity<Object> buildResponse(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> context) {
        String requestId = requestContext.getRequestId();
        return new ResponseEntity<>(ApiResponse.error(code, message, requestId, context), status);
    }

    private String resolveErrorCode(HttpStatus status) {
        return switch (status) {
            case METHOD_NOT_ALLOWED -> ErrorCodeConstant.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCodeConstant.UNSUPPORTED_MEDIA_TYPE;
            case NOT_ACCEPTABLE -> ErrorCodeConstant.NOT_ACCEPTABLE;
            default -> ErrorCodeConstant.API_ERROR;
        };
    }
}
