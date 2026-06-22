/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;

/**
 * Single source of truth for an apartment's effective occupancy status.
 *
 * <p>Occupancy is DERIVED, not stored. {@code AVAILABLE} and {@code OCCUPIED} are
 * computed from whether the apartment currently has any active resident
 * (a resident with {@code move_out_date IS NULL}). {@code MAINTENANCE} is the only
 * occupancy state that is stored and manually set, and it takes priority over
 * derived occupancy: an apartment under maintenance reports {@code MAINTENANCE}
 * even if residents still live there.
 *
 * <p>Convention: {@code OCCUPIED} is NEVER persisted in {@code apartments.status};
 * it exists only as a computed/response value. The stored column only ever holds
 * {@code AVAILABLE} or {@code MAINTENANCE} (see migration V19). This resolver is the
 * ONE place the AVAILABLE/OCCUPIED/MAINTENANCE rule is expressed — the apartment list,
 * apartment detail, dashboard KPI, and resident report all route through it so they
 * cannot disagree.
 */
public final class OccupancyResolver {

    /**
     * Private constructor — utility class, never instantiated.
     */
    private OccupancyResolver() {
    }

    /**
     * Resolves the effective occupancy status from the stored status and active-resident presence.
     *
     * @param storedStatus      the value persisted in {@code apartments.status}
     *                          (only {@code AVAILABLE} or {@code MAINTENANCE} post-V19;
     *                          a stray {@code OCCUPIED} is treated as non-maintenance).
     * @param hasActiveResident {@code true} if the apartment has ≥1 resident with no move-out date.
     * @return {@code MAINTENANCE} when the stored status is {@code MAINTENANCE} (priority);
     *         otherwise {@code OCCUPIED} when there is an active resident, else {@code AVAILABLE}.
     */
    public static ApartmentStatus effective(ApartmentStatus storedStatus, boolean hasActiveResident) {
        // MAINTENANCE is a manual, stored state and overrides derived occupancy (CTO ruling).
        if (storedStatus == ApartmentStatus.MAINTENANCE) {
            return ApartmentStatus.MAINTENANCE;
        }
        // AVAILABLE vs OCCUPIED is derived purely from active-resident presence.
        return hasActiveResident ? ApartmentStatus.OCCUPIED : ApartmentStatus.AVAILABLE;
    }
}
