package com.ecommerce.common.exception;

import com.ecommerce.common.response.ErrorResponse;
import com.ecommerce.common.response.FieldErrorDetail;
import com.ecommerce.common.utility.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all controllers.
 * Produces structured RFC-7807 aligned error responses with correlation IDs.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex, HttpServletRequest request) {
        log.warn("Business domain exception [{}]: {} at {}", ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        ErrorResponse response = buildErrorResponse(
                ex.getStatus(),
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        BindingResult result = ex.getBindingResult();
        List<FieldErrorDetail> fieldErrors = result.getFieldErrors().stream()
                .map(err -> FieldErrorDetail.builder()
                        .field(err.getField())
                        .rejectedValue(err.getRejectedValue())
                        .message(err.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        log.warn("Validation failure at {}: {} field error(s)", request.getRequestURI(), fieldErrors.size());

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Input payload validation failed. Please check field errors.",
                request.getRequestURI(),
                fieldErrors
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> FieldErrorDetail.builder()
                        .field(cv.getPropertyPath().toString())
                        .rejectedValue(cv.getInvalidValue())
                        .message(cv.getMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "CONSTRAINT_VIOLATION",
                "Request parameter validation failed.",
                request.getRequestURI(),
                fieldErrors
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed: Bad credentials at {}", request.getRequestURI());
        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount(LockedException ex, HttpServletRequest request) {
        log.warn("Authentication failed: Account locked at {}", request.getRequestURI());
        ErrorResponse response = buildErrorResponse(
                HttpStatus.LOCKED,
                "ACCOUNT_LOCKED",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.LOCKED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex, HttpServletRequest request) {
        log.warn("Authentication failed: Account disabled at {}", request.getRequestURI());
        ErrorResponse response = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_DISABLED",
                "Account is disabled. Please verify your email or contact support.",
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse response = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication required at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Full authentication is required to access this resource",
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        ErrorResponse response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "ENDPOINT_NOT_FOUND",
                "The requested endpoint does not exist: " + request.getRequestURI(),
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ErrorResponse response = buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint",
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "Required request parameter '" + ex.getParameterName() + "' is missing",
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred. Please contact system support.",
                request.getRequestURI(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ErrorResponse buildErrorResponse(HttpStatus status, String errorCode, String message, String path, List<FieldErrorDetail> fieldErrors) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
        return ErrorResponse.builder()
                .success(false)
                .status(status.value())
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();
    }
}

