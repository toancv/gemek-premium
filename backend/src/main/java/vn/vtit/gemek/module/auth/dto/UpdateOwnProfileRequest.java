/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/auth/me/profile} (authenticated, any role).
 *
 * <p>Carries ONLY the self-editable profile fields. It deliberately does NOT
 * include {@code role}, {@code isActive}, {@code password}, or {@code id}:
 * identity is server-derived from the principal, and those fields are the
 * privilege-escalation surface the admin-only {@code UpdateUserRequest} owns.
 * Because this is a record, any extra JSON keys a crafted request might smuggle
 * are simply ignored at bind time.
 *
 * @param fullName the caller's display name.
 * @param phone    the caller's phone (login identifier); canonical form enforced by
 *                 {@code PhoneUtils.normalize()} in the service.
 * @param email    optional informational email; unique when provided, blank treated as null.
 */
public record UpdateOwnProfileRequest(

        @NotBlank(message = "Full name is required.")
        @Size(max = 255, message = "Full name must not exceed 255 characters.")
        String fullName,

        @NotBlank(message = "Phone number is required.")
        @Size(max = 20, message = "Phone must not exceed 20 characters.")
        String phone,

        @Email(message = "Email must be a valid address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email
) {}
