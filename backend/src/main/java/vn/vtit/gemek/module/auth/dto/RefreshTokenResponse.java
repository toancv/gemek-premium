/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

/**
 * Response body for {@code POST /api/auth/refresh}.
 *
 * @param accessToken new signed JWT access token (15-minute expiry).
 * @param expiresIn   access token lifetime in seconds (900).
 */
public record RefreshTokenResponse(
        String accessToken,
        long expiresIn
) {}
