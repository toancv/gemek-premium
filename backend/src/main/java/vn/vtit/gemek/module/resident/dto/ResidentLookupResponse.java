/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Result of a phone-keyed resident lookup, used by the two-step place-resident flow.
 *
 * <p>ADMIN-only and deliberately minimal: it returns just enough for an admin to recognize the person
 * (display name + the apartment(s) they are CURRENTLY actively residing in) so the operator can decide
 * whether to reuse the existing profile. It NEVER leaks full PII (phone, email, date of birth, password,
 * audit fields). The same shape is embedded as {@code matched} in the
 * {@link vn.vtit.gemek.common.exception.ErrorCode#REUSE_CONFIRMATION_REQUIRED} response so the frontend can
 * render the confirm popup without a second round-trip.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidentLookupResponse {

    /** Branch status the server resolved the phone to. */
    private LookupStatus status;

    /**
     * Display name of the matched existing user, or {@code null} for {@link LookupStatus#NEW}.
     */
    private String displayName;

    /**
     * The apartments the matched user is currently an active resident of. Empty for
     * {@link LookupStatus#NEW} and {@link LookupStatus#MOVED_OUT}.
     */
    private List<ApartmentRef> activeApartments;

    /**
     * Branch status resolved from a phone (and optionally a target apartment).
     */
    public enum LookupStatus {

        /** Phone not found — a brand-new resident/user will be created. */
        NEW,

        /** User exists and has at least one active residency in (an)other apartment(s). */
        ACTIVE_ELSEWHERE,

        /** User exists but has no active residency (account moved out / disabled). */
        MOVED_OUT,

        /** User exists and already actively resides in the supplied target apartment. */
        ALREADY_HERE
    }

    /**
     * Minimal apartment identifier — no resident PII attached.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApartmentRef {

        /** Apartment UUID. */
        private UUID id;

        /** Apartment unit number within its block. */
        private String unitNumber;

        /** Display name of the block the apartment belongs to. */
        private String blockName;
    }
}
