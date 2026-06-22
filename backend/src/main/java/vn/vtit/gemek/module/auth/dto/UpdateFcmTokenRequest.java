/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/auth/me/fcm-token}.
 *
 * @param fcmToken the Firebase Cloud Messaging device registration token.
 */
public record UpdateFcmTokenRequest(

        @NotBlank(message = "FCM token is required.")
        @Size(max = 500, message = "FCM token must not exceed 500 characters.")
        String fcmToken
) {}
