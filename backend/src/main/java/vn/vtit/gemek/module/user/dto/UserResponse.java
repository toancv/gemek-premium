/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary user response returned in paginated list and POST/PUT responses.
 *
 * @param id          the user's unique identifier.
 * @param email       the user's email address.
 * @param fullName    the user's display name.
 * @param phone       the user's phone number, or {@code null}.
 * @param role        the user's role.
 * @param dateOfBirth the user's date of birth, or {@code null}.
 * @param isActive    whether the account is active.
 * @param createdAt   record creation timestamp.
 */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        LocalDate dateOfBirth,
        @JsonProperty("isActive") boolean isActive,
        OffsetDateTime createdAt
) {}
