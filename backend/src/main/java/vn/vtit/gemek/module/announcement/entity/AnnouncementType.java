/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.entity;

/**
 * Enumeration of announcement content types.
 *
 * <p>Maps to the PostgreSQL {@code announcement_type} ENUM defined in V1 migration.
 * Controls how the announcement is displayed and prioritised in the resident portal.
 */
public enum AnnouncementType {

    /** Standard informational notice. */
    GENERAL,

    /** Time-sensitive or safety-critical notice requiring immediate attention. */
    URGENT,

    /** Notice about planned or unplanned building maintenance works. */
    MAINTENANCE,

    /** Notice about amenity availability, rules, or changes. */
    AMENITY,

    /** Notice about a building community event. */
    EVENT
}
