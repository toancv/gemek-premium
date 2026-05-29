/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.exception;

/**
 * Application-level runtime exception carrying a typed {@link ErrorCode}.
 *
 * <p>Thrown by service layer methods to signal business rule violations or resource
 * lookup failures. Caught and translated by {@link GlobalExceptionHandler}.
 */
public class AppException extends RuntimeException {

    /** Typed error code used to determine the HTTP response status and error body. */
    private final ErrorCode errorCode;

    /**
     * Constructs an {@link AppException} with a typed error code and human-readable message.
     *
     * @param errorCode the {@link ErrorCode} classifying the failure.
     * @param message   human-readable description of the failure.
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs an {@link AppException} with a typed error code, message, and root cause.
     *
     * @param errorCode the {@link ErrorCode} classifying the failure.
     * @param message   human-readable description of the failure.
     * @param cause     the underlying exception.
     */
    public AppException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the typed error code.
     *
     * @return the {@link ErrorCode}.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
