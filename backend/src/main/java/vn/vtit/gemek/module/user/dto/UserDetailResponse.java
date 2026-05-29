/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.dto;

import vn.vtit.gemek.module.user.entity.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Detailed user response including avatar and last login timestamp.
 * Used for {@code GET /api/auth/me} and {@code GET /api/users/{id}}.
 *
 * @param id          the user's unique identifier.
 * @param email       the user's email address.
 * @param fullName    the user's display name.
 * @param phone       the user's phone number, or {@code null}.
 * @param role        the user's role.
 * @param avatarUrl   the user's avatar MinIO object key, or {@code null}.
 * @param isActive    whether the account is active.
 * @param lastLoginAt timestamp of the most recent successful login, or {@code null}.
 * @param createdAt   record creation timestamp.
 */
public record UserDetailResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        String avatarUrl,
        boolean isActive,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt
) {}
