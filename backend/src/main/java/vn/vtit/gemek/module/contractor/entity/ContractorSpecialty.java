/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.entity;

/**
 * Enumeration of contractor specialties, mapped to the PostgreSQL {@code contractor_specialty} ENUM type.
 */
public enum ContractorSpecialty {
    /** General cleaning services. */
    CLEANING,
    /** Security personnel and systems. */
    SECURITY,
    /** Elevator maintenance and repair. */
    ELEVATOR,
    /** Fire safety systems. */
    FIRE_SAFETY,
    /** Landscaping and green areas. */
    LANDSCAPING,
    /** Pest control services. */
    PEST_CONTROL,
    /** Electrical installation and repair. */
    ELECTRICAL,
    /** Plumbing installation and repair. */
    PLUMBING,
    /** All other specialties. */
    OTHER
}
