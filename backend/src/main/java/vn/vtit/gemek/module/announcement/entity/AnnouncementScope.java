/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.entity;

/**
 * Enumeration of announcement delivery scopes.
 *
 * <p>Maps to the PostgreSQL {@code announcement_scope} ENUM defined in V1 migration.
 * Controls which residents receive the announcement based on their assigned apartment.
 *
 * <ul>
 *   <li>{@link #ALL} — sent to every resident in the building.</li>
 *   <li>{@link #BLOCK} — sent only to residents in a specific block; requires {@code targetBlockId}.</li>
 *   <li>{@link #FLOOR} — sent only to residents on a specific floor of a specific block;
 *       requires both {@code targetBlockId} and {@code targetFloor}.</li>
 * </ul>
 */
public enum AnnouncementScope {

    /** Broadcast to all residents in the building. */
    ALL,

    /** Scoped to a specific building block. */
    BLOCK,

    /** Scoped to a specific floor within a building block. */
    FLOOR
}
