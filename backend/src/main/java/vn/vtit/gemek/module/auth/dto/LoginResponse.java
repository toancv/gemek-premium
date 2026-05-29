/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.UUID;

/**
 * Response body for {@code POST /api/auth/login}.
 *
 * @param accessToken  signed JWT access token (15-minute expiry).
 * @param refreshToken signed JWT refresh token (7-day expiry).
 * @param expiresIn    access token lifetime in seconds (900).
 * @param user         summary of the authenticated user.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserSummary user
) {

    /**
     * Embedded user summary returned inside the login response.
     *
     * @param id        the user's unique identifier.
     * @param email     the user's email address.
     * @param fullName  the user's display name.
     * @param role      the user's role.
     * @param avatarUrl the user's avatar object key, or {@code null}.
     */
    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            UserRole role,
            String avatarUrl
    ) {}
}
