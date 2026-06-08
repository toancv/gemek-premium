/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global REST exception handler translating all application exceptions into
 * the standard error response format defined in the API specification.
 *
 * <p>Standard error response shape:
 * <pre>
 * {
 *   "error":     "ERROR_CODE",
 *   "message":   "Human-readable description",
 *   "timestamp": "2026-05-29T10:00:00Z",
 *   "path":      "/api/tickets"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles {@link AppException} thrown by service layer methods.
     *
     * @param ex      the application exception.
     * @param request the current HTTP request (used to populate the {@code path} field).
     * @return standardised error response with the HTTP status from the error code.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(
            AppException ex, HttpServletRequest request) {
        log.warn("AppException on {}: {} — {}", request.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(ex.getErrorCode().name(), ex.getMessage(),
                ex.getErrorCode().getHttpStatus(), request.getRequestURI());
    }

    /**
     * Handles Jakarta Bean Validation failures from {@code @Valid} annotated request bodies.
     *
     * <p>Concatenates all field-level violations into a single message.
     *
     * @param ex      the validation exception.
     * @param request the current HTTP request.
     * @return 400 VALIDATION_ERROR response listing all violated constraints.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Build a readable summary of all field violations.
        StringBuilder messageBuilder = new StringBuilder();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            if (messageBuilder.length() > 0) {
                messageBuilder.append("; ");
            }
            messageBuilder.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage());
        }
        log.warn("Validation error on {}: {}", request.getRequestURI(), messageBuilder);
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR.name(), messageBuilder.toString(),
                HttpStatus.BAD_REQUEST, request.getRequestURI());
    }

    /**
     * Handles type conversion failures for {@code @RequestParam} values (e.g. invalid enum input).
     *
     * <p>Without this handler, Spring's default behaviour propagates an {@link IllegalArgumentException}
     * that hits the catch-all and returns 500. This handler returns 400 VALIDATION_ERROR instead.
     *
     * @param ex      the type mismatch exception.
     * @param request the current HTTP request.
     * @return 400 VALIDATION_ERROR response with the offending parameter and value.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.";
        log.warn("Type mismatch on {}: {}", request.getRequestURI(), message);
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR.name(), message,
                HttpStatus.BAD_REQUEST, request.getRequestURI());
    }

    /**
     * Handles DB unique-constraint violations (e.g. duplicate phone or email) that reach the
     * handler without being caught by the service-layer guard — typically from a race condition.
     *
     * <p>Without this handler, {@link DataIntegrityViolationException} falls to the catch-all
     * and returns 500, bypassing the localized error-toast path on the frontend.
     *
     * @param ex      the constraint violation exception.
     * @param request the current HTTP request.
     * @return 409 CONFLICT response.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation on {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return buildErrorResponse(ErrorCode.CONFLICT.name(),
                "A unique constraint was violated — check phone or email for duplicates.",
                HttpStatus.CONFLICT, request.getRequestURI());
    }

    /**
     * Handles Spring Security {@link AccessDeniedException} (valid token, insufficient role).
     *
     * @param ex      the access denied exception.
     * @param request the current HTTP request.
     * @return 403 FORBIDDEN response.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(ErrorCode.FORBIDDEN.name(), "Access denied — insufficient permissions.",
                HttpStatus.FORBIDDEN, request.getRequestURI());
    }

    /**
     * Handles Spring Security {@link AuthenticationException} (missing or invalid token).
     *
     * @param ex      the authentication exception.
     * @param request the current HTTP request.
     * @return 401 UNAUTHORIZED response.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(ErrorCode.UNAUTHORIZED.name(), "Authentication required.",
                HttpStatus.UNAUTHORIZED, request.getRequestURI());
    }

    /**
     * Catch-all handler for unexpected runtime exceptions.
     *
     * <p>Logs the full stack trace at ERROR level. Returns a generic 500 response
     * without exposing internal error details to callers.
     *
     * @param ex      the unexpected exception.
     * @param request the current HTTP request.
     * @return 500 INTERNAL_ERROR response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR.name(), "An unexpected error occurred.",
                HttpStatus.INTERNAL_SERVER_ERROR, request.getRequestURI());
    }

    /**
     * Builds the standard error response body map.
     *
     * @param errorCode  the error code string.
     * @param message    human-readable description.
     * @param httpStatus the HTTP status to return.
     * @param path       the request URI.
     * @return {@link ResponseEntity} containing the error map.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            String errorCode, String message, HttpStatus httpStatus, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", errorCode);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        body.put("path", path);
        return ResponseEntity.status(httpStatus).body(body);
    }
}
