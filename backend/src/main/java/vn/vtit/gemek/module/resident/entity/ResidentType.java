/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.entity;

/**
 * Enumeration of resident assignment types.
 *
 * <p>Maps to the PostgreSQL {@code resident_type} ENUM type defined in V1 migration.
 */
public enum ResidentType {

    /** The resident owns the apartment. */
    OWNER,

    /** The resident is a tenant renting the apartment. */
    TENANT
}
