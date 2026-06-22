/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.vtit.gemek.module.amenity.repository.AmenityBookingRepository;
import vn.vtit.gemek.module.amenity.repository.AmenityRepository;
import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.report.dto.DashboardResponse;
import vn.vtit.gemek.module.report.dto.ResidentReportResponse;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportServiceImpl} occupancy derivation.
 *
 * <p>Guards two things: (1) occupancy is derived via the shared rule with MAINTENANCE priority,
 * and (2) the dashboard and resident-report occupancy numbers converge (the core regression that
 * caused the diagnosis to show 10 vs 1622).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {

    @Mock private ApartmentRepository apartmentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AmenityRepository amenityRepository;
    @Mock private AmenityBookingRepository amenityBookingRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ResidentRepository residentRepository;

    private ReportServiceImpl service;

    private UUID apt1;
    private UUID apt2;
    private UUID apt3;
    private UUID apt4;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(
                apartmentRepository, ticketRepository, amenityRepository,
                amenityBookingRepository, contractRepository, residentRepository);

        apt1 = UUID.randomUUID();
        apt2 = UUID.randomUUID();
        apt3 = UUID.randomUUID();
        apt4 = UUID.randomUUID();

        // Shared occupancy fixture (block-agnostic, blockId = null):
        // apt1, apt2 → AVAILABLE stored + active resident → OCCUPIED
        // apt3       → MAINTENANCE stored + active resident → MAINTENANCE (priority, NOT occupied)
        // apt4       → AVAILABLE stored + no resident       → AVAILABLE
        when(apartmentRepository.findIdAndStatus(any())).thenReturn(List.of(
                new Object[]{apt1, ApartmentStatus.AVAILABLE},
                new Object[]{apt2, ApartmentStatus.AVAILABLE},
                new Object[]{apt3, ApartmentStatus.MAINTENANCE},
                new Object[]{apt4, ApartmentStatus.AVAILABLE}));
        when(residentRepository.findOccupiedApartmentIds(any()))
                .thenReturn(List.of(apt1, apt2, apt3));

        // Demographics (only resident-mix figures are consumed from this row).
        when(residentRepository.getResidentDemographics(any()))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 3L, 2L, 99L}));

        // Minimal stubs so getDashboard() can assemble the unrelated KPI blocks.
        when(ticketRepository.getDashboardTicketKpis(any())).thenReturn(List.of());
        when(ticketRepository.countActiveByCategory()).thenReturn(List.of());
    }

    @Test
    @DisplayName("dashboard — occupancy derived with MAINTENANCE priority")
    void dashboard_derivesOccupancy_maintenancePriority() {
        DashboardResponse.ApartmentStats stats = service.getDashboard().apartments();

        assertThat(stats.total()).isEqualTo(4);
        assertThat(stats.occupied()).isEqualTo(2);       // apt1, apt2 (apt3 is MAINTENANCE)
        assertThat(stats.maintenance()).isEqualTo(1);    // apt3
        assertThat(stats.available()).isEqualTo(1);      // apt4
        assertThat(stats.occupancyRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("dashboard and resident-report occupancy converge (same DB → same numbers)")
    void dashboardAndResidentReport_occupancyConverges() {
        long dashboardOccupied = service.getDashboard().apartments().occupied();
        ResidentReportResponse report = service.getResidentReport(null);

        assertThat(report.occupiedApartments()).isEqualTo(dashboardOccupied);
        assertThat(dashboardOccupied).isEqualTo(2);
        assertThat(report.totalApartments()).isEqualTo(4);
        assertThat(report.occupancyRate()).isEqualTo(0.5);
    }
}
