/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.entity;

/**
 * Enumeration of user roles.
 *
 * <p>Values must match the PostgreSQL {@code user_role} ENUM type exactly.
 * Mapped via {@code @Enumerated(EnumType.STRING)} on the {@link User} entity.
 */
public enum UserRole {

    /** Building manager — full control over all modules. */
    ADMIN,

    /** Internal maintenance/operations staff — works assigned tickets. */
    TECHNICIAN,

    /** Apartment resident — personal portal access. */
    RESIDENT,

    /** Read-only access to reports and dashboard. */
    BOARD_MEMBER
}
