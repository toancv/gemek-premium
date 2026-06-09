/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Enumeration of all application-level error codes with their associated HTTP status.
 *
 * <p>Error codes are returned in the {@code error} field of the standard error response body.
 * Each constant maps directly to the API specification error code table.
 */
public enum ErrorCode {

    /** Missing or invalid JWT token. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /** Valid token but insufficient role or resource ownership. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** Resource does not exist. */
    NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Request body or query param fails validation. */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    /** Duplicate or invalid state transition. */
    CONFLICT(HttpStatus.CONFLICT),

    /** Unexpected server error. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /** Too many requests — rate limit exceeded. */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    /** Email address already registered by another user. */
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** Phone number already registered by another user. */
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** Login credentials are incorrect. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    /** JWT token is malformed, expired, or revoked. */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),

    /** Contractor assignment attempted on a non-MAINTENANCE_REPAIR ticket. */
    CONTRACTOR_ASSIGNMENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST),

    /** State transition violates the defined workflow. */
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),

    /** User account is deactivated. */
    ACCOUNT_INACTIVE(HttpStatus.UNAUTHORIZED),

    /** Attempt to delete an entity that has active dependencies. */
    HAS_ACTIVE_DEPENDENCIES(HttpStatus.CONFLICT),

    /** Cannot perform action on own account. */
    SELF_OPERATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST),

    /** License plate already registered by another vehicle. */
    LICENSE_PLATE_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** Parking slot number already exists. */
    SLOT_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** Ticket rating has already been submitted. */
    TICKET_ALREADY_RATED(HttpStatus.CONFLICT),

    /** Resident has already moved out. */
    RESIDENT_ALREADY_MOVED_OUT(HttpStatus.CONFLICT);

    /** HTTP status code associated with this error code. */
    private final HttpStatus httpStatus;

    /**
     * Constructs an {@link ErrorCode} with the given HTTP status.
     *
     * @param httpStatus the HTTP status to associate with this error code.
     */
    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the HTTP status associated with this error code.
     *
     * @return the {@link HttpStatus}.
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
