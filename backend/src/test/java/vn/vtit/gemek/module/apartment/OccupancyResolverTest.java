/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OccupancyResolver} — the single occupancy rule.
 */
class OccupancyResolverTest {

    @Test
    @DisplayName("AVAILABLE + active resident → OCCUPIED")
    void available_withActiveResident_occupied() {
        assertThat(OccupancyResolver.effective(ApartmentStatus.AVAILABLE, true))
                .isEqualTo(ApartmentStatus.OCCUPIED);
    }

    @Test
    @DisplayName("AVAILABLE + no active resident → AVAILABLE")
    void available_noActiveResident_available() {
        assertThat(OccupancyResolver.effective(ApartmentStatus.AVAILABLE, false))
                .isEqualTo(ApartmentStatus.AVAILABLE);
    }

    @Test
    @DisplayName("MAINTENANCE + active resident → MAINTENANCE (priority)")
    void maintenance_withActiveResident_maintenancePriority() {
        assertThat(OccupancyResolver.effective(ApartmentStatus.MAINTENANCE, true))
                .isEqualTo(ApartmentStatus.MAINTENANCE);
    }

    @Test
    @DisplayName("MAINTENANCE + no active resident → MAINTENANCE")
    void maintenance_noActiveResident_maintenance() {
        assertThat(OccupancyResolver.effective(ApartmentStatus.MAINTENANCE, false))
                .isEqualTo(ApartmentStatus.MAINTENANCE);
    }

    @Test
    @DisplayName("stray stored OCCUPIED is treated as non-maintenance and re-derived")
    void strayStoredOccupied_reDerived() {
        assertThat(OccupancyResolver.effective(ApartmentStatus.OCCUPIED, false))
                .isEqualTo(ApartmentStatus.AVAILABLE);
        assertThat(OccupancyResolver.effective(ApartmentStatus.OCCUPIED, true))
                .isEqualTo(ApartmentStatus.OCCUPIED);
    }
}
