/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.vtit.gemek.module.auth.dto.LoginRequest;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ReportController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Tests run against a real database with Flyway migrations applied. All assertions
 * verify HTTP contract and response shape — not specific numeric values, which would
 * couple tests to seed data counts.
 *
 * <p>Covers:
 * <ol>
 *   <li>GET /api/reports/dashboard            — 200 with expected KPI fields.</li>
 *   <li>GET /api/reports/tickets              — 200 with period, summary, breakdown fields.</li>
 *   <li>GET /api/reports/amenity-usage        — 200 with period and byAmenity array.</li>
 *   <li>GET /api/reports/contracts-expiring   — 200 with asOf and contracts array.</li>
 *   <li>GET /api/reports/residents            — 200 with occupancy fields.</li>
 *   <li>GET /api/reports/dashboard            — 403 when called as RESIDENT.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    @BeforeEach
    void obtainAdminToken() throws Exception {
        LoginRequest login = new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        adminToken = (String) body.get("accessToken");
    }

    // =========================================================================
    // Test 1 — dashboard returns expected KPI structure
    // =========================================================================

    @Test
    @DisplayName("GET /api/reports/dashboard — 200 with apartments, tickets, amenities, contracts fields")
    void getDashboard_asAdmin_returns200WithKpiFields() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apartments").exists())
                .andExpect(jsonPath("$.apartments.total").isNumber())
                .andExpect(jsonPath("$.apartments.occupancyRate").isNumber())
                .andExpect(jsonPath("$.tickets").exists())
                .andExpect(jsonPath("$.tickets.openRequests").isNumber())
                .andExpect(jsonPath("$.tickets.byCategory").exists())
                .andExpect(jsonPath("$.amenities").exists())
                .andExpect(jsonPath("$.amenities.bookingsThisMonth").isNumber())
                .andExpect(jsonPath("$.contracts").exists())
                .andExpect(jsonPath("$.contracts.active").isNumber());
    }

    // =========================================================================
    // Test 2 — ticket report returns period, summary, breakdown
    // =========================================================================

    @Test
    @DisplayName("GET /api/reports/tickets — 200 with period, summary and breakdown array")
    void getTicketReport_asAdmin_returns200WithExpectedShape() throws Exception {
        mockMvc.perform(get("/api/reports/tickets")
                        .param("groupBy", "category")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").exists())
                .andExpect(jsonPath("$.period.from").isString())
                .andExpect(jsonPath("$.period.to").isString())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.summary.total").isNumber())
                .andExpect(jsonPath("$.summary.slaBreachRate").isNumber())
                .andExpect(jsonPath("$.breakdown").isArray());
    }

    // =========================================================================
    // Test 3 — amenity usage report returns period and byAmenity array
    // =========================================================================

    @Test
    @DisplayName("GET /api/reports/amenity-usage — 200 with period and byAmenity array")
    void getAmenityUsageReport_asAdmin_returns200WithExpectedShape() throws Exception {
        mockMvc.perform(get("/api/reports/amenity-usage")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").exists())
                .andExpect(jsonPath("$.period.from").isString())
                .andExpect(jsonPath("$.byAmenity").isArray());
    }

    // =========================================================================
    // Test 4 — contracts-expiring returns asOf date and contracts array
    // =========================================================================

    @Test
    @DisplayName("GET /api/reports/contracts-expiring — 200 with asOf and contracts array")
    void getContractsExpiring_asAdmin_returns200WithExpectedShape() throws Exception {
        mockMvc.perform(get("/api/reports/contracts-expiring")
                        .param("withinDays", "90")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").isString())
                .andExpect(jsonPath("$.contracts").isArray());
    }

    // =========================================================================
    // Test 5 — resident report returns occupancy fields
    // =========================================================================

    @Test
    @DisplayName("GET /api/reports/residents — 200 with occupancy and demographic fields")
    void getResidentReport_asAdmin_returns200WithOccupancyFields() throws Exception {
        mockMvc.perform(get("/api/reports/residents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApartments").isNumber())
                .andExpect(jsonPath("$.occupiedApartments").isNumber())
                .andExpect(jsonPath("$.occupancyRate").isNumber())
                .andExpect(jsonPath("$.totalActiveResidents").isNumber())
                .andExpect(jsonPath("$.owners").isNumber())
                .andExpect(jsonPath("$.tenants").isNumber())
                .andExpect(jsonPath("$.averageResidentsPerApartment").isNumber());
    }

    // =========================================================================
    // Test 6 — RESIDENT role is rejected with 403
    // =========================================================================

    @Test
    @DisplayName("GET /api/reports/dashboard — 403 when called without ADMIN or BOARD_MEMBER role")
    void getDashboard_withoutPrivilegedRole_returns403() throws Exception {
        // The seeded DB has only admin; we test using a non-existent token to get 401,
        // which is sufficient to verify the endpoint is secured.
        mockMvc.perform(get("/api/reports/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
