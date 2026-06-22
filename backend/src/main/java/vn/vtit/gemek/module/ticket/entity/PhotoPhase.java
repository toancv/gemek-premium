/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.entity;

/**
 * Enumeration of photo phases for ticket documentation, mapped to the PostgreSQL {@code photo_phase} ENUM type.
 */
public enum PhotoPhase {
    /** Photo taken before work begins. Residents may only upload BEFORE photos. */
    BEFORE,
    /** Photo taken while work is in progress. */
    PROGRESS,
    /** Photo taken after work is completed. */
    AFTER
}
