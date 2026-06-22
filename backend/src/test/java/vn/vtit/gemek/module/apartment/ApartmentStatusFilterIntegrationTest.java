/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;
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
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateApartmentRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the apartment list {@code ?status=} filter.
 *
 * <p>The filter must select by EFFECTIVE (derived) occupancy — identical to the status the
 * list/detail/dashboard surfaces DISPLAY via {@link OccupancyResolver}:
 * <ul>
 *   <li>{@code OCCUPIED} → has ≥1 active resident AND stored != MAINTENANCE;</li>
 *   <li>{@code AVAILABLE} → no active resident AND stored != MAINTENANCE;</li>
 *   <li>{@code MAINTENANCE} → stored == MAINTENANCE (priority, regardless of residents).</li>
 * </ul>
 *
 * <p>Fixtures (one block): OCC-1 (occupied), AVL-1 (vacant), MNT-1 (maintenance, no resident),
 * MNT-2 (maintenance + resident). The agreement test asserts each {@code ?status=X} returns
 * exactly the fixtures whose displayed status is X — locking filter and display together so
 * the SQL effective-status predicate cannot drift from {@code OccupancyResolver}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApartmentStatusFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";
    private static final String DEFAULT_PASS   = "Resident@123456";

    private UUID blockId;

    @BeforeEach
    void setUp() throws Exception {
        LoginRequest login = new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        adminToken = (String) body.get("accessToken");

        // Build the 4-fixture block: occupied / vacant / maintenance / maintenance+resident.
        blockId = createBlock("FilterBlock-" + System.nanoTime());

        UUID occ = createApartment("OCC-1");
        createResident(uniquePhone(), occ);

        createApartment("AVL-1");

        UUID mnt1 = createApartment("MNT-1");
        setMaintenance(mnt1, "MNT-1");

        UUID mnt2 = createApartment("MNT-2");
        createResident(uniquePhone(), mnt2);
        setMaintenance(mnt2, "MNT-2");
    }

    // =========================================================================
    // ?status=OCCUPIED → only apartments with active residents (not maintenance)
    // =========================================================================

    @Test
    @DisplayName("GET /api/apartments?status=OCCUPIED — only apartments with an active resident")
    void filterOccupied_returnsOnlyApartmentsWithActiveResidents() throws Exception {
        mockMvc.perform(get("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("blockId", blockId.toString())
                        .param("status", "OCCUPIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unitNumber").value("OCC-1"))
                .andExpect(jsonPath("$.data[0].status").value("OCCUPIED"));
    }

    // =========================================================================
    // ?status=AVAILABLE → only truly-vacant; MUST NOT return occupied (CTO bug)
    // =========================================================================

    @Test
    @DisplayName("GET /api/apartments?status=AVAILABLE — only vacant; excludes occupied (CTO bug)")
    void filterAvailable_returnsOnlyVacant_excludesOccupied() throws Exception {
        mockMvc.perform(get("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("blockId", blockId.toString())
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unitNumber").value("AVL-1"))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"))
                // The CTO bug: AVAILABLE used to return occupied units. None may appear now.
                .andExpect(jsonPath("$.data[?(@.status == 'OCCUPIED')]").isEmpty());
    }

    // =========================================================================
    // ?status=MAINTENANCE → maintenance units incl. one with a resident (priority)
    // =========================================================================

    @Test
    @DisplayName("GET /api/apartments?status=MAINTENANCE — maintenance units incl. with-resident (priority)")
    void filterMaintenance_returnsMaintenanceInclWithResident() throws Exception {
        mockMvc.perform(get("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("blockId", blockId.toString())
                        .param("status", "MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].unitNumber",
                        org.hamcrest.Matchers.containsInAnyOrder("MNT-1", "MNT-2")))
                .andExpect(jsonPath("$.data[*].status", everyItem(org.hamcrest.Matchers.equalTo("MAINTENANCE"))));
    }

    // =========================================================================
    // no status filter → all fixtures
    // =========================================================================

    @Test
    @DisplayName("GET /api/apartments — no status filter returns all fixtures")
    void noStatusFilter_returnsAll() throws Exception {
        mockMvc.perform(get("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("blockId", blockId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    // =========================================================================
    // Filter↔display agreement + count-query consistency (the single-source guarantee)
    // =========================================================================

    @Test
    @DisplayName("Filter results match OccupancyResolver classification; total == rows for each status")
    void filterMatchesDisplayedStatus_andCountConsistent() throws Exception {
        // For each effective status: every returned item displays that status (agreement), and
        // the page total equals the number of returned rows (count query == row query predicate).
        for (String effective : new String[] {"OCCUPIED", "AVAILABLE", "MAINTENANCE"}) {
            int expectedTotal = "MAINTENANCE".equals(effective) ? 2 : 1;
            mockMvc.perform(get("/api/apartments")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("blockId", blockId.toString())
                            .param("status", effective))
                    .andExpect(status().isOk())
                    // Count consistency: declared total equals the actual row count returned.
                    .andExpect(jsonPath("$.total").value(expectedTotal))
                    .andExpect(jsonPath("$.data.length()").value(expectedTotal))
                    // Agreement: displayed status of every returned row equals the filter.
                    .andExpect(jsonPath("$.data[*].status", everyItem(org.hamcrest.Matchers.equalTo(effective))));
        }
    }

    // ── fixture helpers ───────────────────────────────────────────────────────

    private UUID createBlock(String name) throws Exception {
        CreateBlockRequest req = new CreateBlockRequest(name, null);
        MvcResult result = mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private UUID createApartment(String unitNumber) throws Exception {
        CreateApartmentRequest req = new CreateApartmentRequest(blockId, (short) 1, unitNumber, null, null);
        MvcResult result = mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private void setMaintenance(UUID apartmentId, String unitNumber) throws Exception {
        UpdateApartmentRequest req = new UpdateApartmentRequest(
                (short) 1, unitNumber, null,
                vn.vtit.gemek.module.apartment.entity.ApartmentStatus.MAINTENANCE, null);
        mockMvc.perform(put("/api/apartments/" + apartmentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private void createResident(String phone, UUID apartmentId) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Filter Resident");
        req.put("phone", phone);
        req.put("dateOfBirth", "1990-01-01");
        req.put("password", DEFAULT_PASS);
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "TENANT");
        req.put("moveInDate", "2026-01-01");
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private static String uniquePhone() {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }
}
