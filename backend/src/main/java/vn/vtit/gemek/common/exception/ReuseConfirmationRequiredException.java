/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.exception;

import vn.vtit.gemek.module.resident.dto.ResidentLookupResponse;

/**
 * Signals that a place-resident request matched an existing user (by phone) who is not active in the
 * target apartment, and the caller did not pass {@code confirmReuse=true}.
 *
 * <p>Carries the matched user's identifying info so {@link GlobalExceptionHandler} can return it in the
 * response body (under {@code matched}) — the frontend renders a confirm popup and re-submits with
 * {@code confirmReuse=true}. The server NEVER trusts the frontend's step-1 lookup; this is the
 * server-side gate that independently re-resolves the phone before any write.
 */
public class ReuseConfirmationRequiredException extends AppException {

    /** Matched existing user's minimal identifying info (name + active apartments). */
    private final transient ResidentLookupResponse matched;

    /**
     * Constructs the exception with the matched user's lookup info and a human-readable message.
     *
     * @param matched the matched user's minimal identifying info.
     * @param message human-readable description of the required confirmation.
     */
    public ReuseConfirmationRequiredException(ResidentLookupResponse matched, String message) {
        super(ErrorCode.REUSE_CONFIRMATION_REQUIRED, message);
        this.matched = matched;
    }

    /**
     * Returns the matched user's minimal identifying info.
     *
     * @return the matched lookup info.
     */
    public ResidentLookupResponse getMatched() {
        return matched;
    }
}
