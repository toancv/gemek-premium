/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/refresh}.
 *
 * @param refreshToken the refresh token to exchange for a new access token.
 */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required.")
        String refreshToken
) {}
