/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.entity;

/**
 * Recurrence frequencies for a {@link MaintenanceSchedule}, mapped to the PostgreSQL
 * {@code schedule_frequency} ENUM.
 */
public enum ScheduleFrequency {
    /** Runs every day. */
    DAILY,
    /** Runs every week. */
    WEEKLY,
    /** Runs every month. */
    MONTHLY,
    /** Runs every quarter (3 months). */
    QUARTERLY,
    /** Runs once per year. */
    ANNUAL
}
