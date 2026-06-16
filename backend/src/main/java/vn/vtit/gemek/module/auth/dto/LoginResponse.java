/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.UUID;

/**
 * Response body for {@code POST /api/auth/login}.
 *
 * @param accessToken  signed JWT access token (15-minute expiry).
 * @param refreshToken signed JWT refresh token (7-day expiry). NOT serialized into the
 *                     JSON body ({@link JsonIgnore}) — since the hardening close-out the
 *                     refresh token travels ONLY as the httpOnly cookie. The field is
 *                     retained so the controller can read it to build that cookie.
 * @param expiresIn    access token lifetime in seconds (900).
 * @param user         summary of the authenticated user.
 */
public record LoginResponse(
        String accessToken,
        @JsonIgnore String refreshToken,
        long expiresIn,
        UserSummary user
) {

    /**
     * Embedded user summary returned inside the login response.
     *
     * @param id        the user's unique identifier.
     * @param phone     the user's canonical phone number (login identifier).
     * @param fullName  the user's display name.
     * @param role      the user's role.
     * @param avatarUrl the user's avatar object key, or {@code null}.
     */
    public record UserSummary(
            UUID id,
            String phone,
            String fullName,
            UserRole role,
            String avatarUrl
    ) {}
}
