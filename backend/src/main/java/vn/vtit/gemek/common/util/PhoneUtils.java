/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.util;

import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;

/**
 * Utility for normalizing and validating Vietnamese mobile phone numbers.
 *
 * <p>Canonical stored form: {@code 0xxxxxxxxx} — leading zero, 10 digits total,
 * matching the VN mobile prefix pattern {@code ^0[3-9]\d{8}$}.
 *
 * <p>All call sites that persist or look up a phone number MUST go through
 * {@link #normalize(String)} so the {@code UNIQUE} DB constraint on the canonical
 * form is satisfied regardless of what format the caller submits.
 */
public final class PhoneUtils {

    /** VN mobile pattern: 0 + digit 3-9 + 8 more digits = 10 digits total. */
    private static final java.util.regex.Pattern VN_MOBILE =
            java.util.regex.Pattern.compile("^0[3-9]\\d{8}$");

    private PhoneUtils() {}

    /**
     * Normalizes a raw phone string to the canonical {@code 0xxxxxxxxx} form.
     *
     * <p>Accepted input formats (all produce {@code 0962464748}):
     * <ul>
     *   <li>{@code 0962464748}     — already canonical</li>
     *   <li>{@code +84962464748}   — international with {@code +}</li>
     *   <li>{@code 84962464748}    — international without {@code +}</li>
     *   <li>{@code +840962464748}  — international with redundant leading {@code 0}</li>
     *   <li>{@code 840962464748}   — international without {@code +}, redundant leading {@code 0}</li>
     *   <li>{@code 096 246 4748}   — spaces, dots, or hyphens stripped first</li>
     * </ul>
     *
     * @param raw the raw phone string supplied by the caller; may be {@code null}.
     * @return the canonical 10-digit phone string starting with {@code 0}.
     * @throws AppException with {@link ErrorCode#VALIDATION_ERROR} if the input is
     *                      null, blank, contains invalid characters, or does not
     *                      resolve to a valid VN mobile number after normalization.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Phone number must not be blank.");
        }

        // Strip visual separators that users commonly paste.
        String stripped = raw.replaceAll("[\\s.\\-]", "");

        // Strip leading '+'.
        if (stripped.startsWith("+")) {
            stripped = stripped.substring(1);
        }

        // Strip country code '84' and ensure leading '0'.
        if (stripped.startsWith("84")) {
            stripped = stripped.substring(2);
            // After stripping '84', the remainder may already start with '0'
            // (e.g. +840xxxxxxxxx → 0xxxxxxxxx after '84' removal → already correct).
            if (!stripped.startsWith("0")) {
                stripped = "0" + stripped;
            }
        }

        if (!VN_MOBILE.matcher(stripped).matches()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Invalid Vietnamese mobile phone number: " + raw);
        }

        return stripped;
    }

    /**
     * Returns {@code true} if the raw string can be normalized to a valid VN mobile number.
     *
     * <p>This is the non-throwing counterpart of {@link #normalize(String)}, useful for
     * front-end–mirrored input validation without try/catch at the call site.
     *
     * @param raw the raw phone string; may be {@code null}.
     * @return {@code true} if valid; {@code false} otherwise.
     */
    public static boolean isValid(String raw) {
        try {
            normalize(raw);
            return true;
        } catch (AppException ex) {
            return false;
        }
    }
}
