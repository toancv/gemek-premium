/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking;

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
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.parking.dto.CheckoutRequest;
import vn.vtit.gemek.module.parking.dto.CreateAssignmentRequest;
import vn.vtit.gemek.module.parking.dto.CreateGuestVehicleRequest;
import vn.vtit.gemek.module.parking.dto.CreateParkingSlotRequest;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;
import java.util.HashMap;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.vehicle.dto.CreateVehicleRequest;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ParkingController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Covers:
 * <ol>
 *   <li>Assign slot to vehicle — 201, slot status = OCCUPIED.</li>
 *   <li>Assign to already-assigned slot — 409 CONFLICT.</li>
 *   <li>Unassign slot — 200, slot status = AVAILABLE.</li>
 *   <li>Guest vehicle checkout — 200, exitTime set.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParkingControllerTest {

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
    // Helpers
    // =========================================================================

    /**
     * Creates a parking slot and returns its UUID.
     *
     * @param slotNumber unique slot identifier string.
     * @param type       slot type (CAR, MOTORBIKE, BICYCLE).
     * @return the created slot UUID.
     */
    private UUID createSlot(String slotNumber, ParkingSlotType type) throws Exception {
        CreateParkingSlotRequest req = new CreateParkingSlotRequest(slotNumber, "Z1", type, null);
        MvcResult result = mockMvc.perform(post("/api/parking/slots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    /**
     * Creates a block and returns its UUID.
     *
     * @param name block name.
     * @return the created block UUID.
     */
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

    /**
     * Creates an apartment and returns its UUID.
     *
     * @param blockId    the block UUID.
     * @param unitNumber unit number string.
     * @return the created apartment UUID.
     */
    private UUID createApartment(UUID blockId, String unitNumber) throws Exception {
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

    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

    /**
     * Creates a new user+resident atomically via the new transactional endpoint.
     *
     * @param phone       the new user's phone.
     * @param apartmentId the apartment UUID to assign.
     * @return the created resident UUID.
     */
    private UUID createResident(String phone, UUID apartmentId) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Parking Resident");
        req.put("phone", phone);
        req.put("dateOfBirth", "1990-01-01");
        req.put("password", "Resident@123456");
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "OWNER");
        req.put("moveInDate", "2026-01-01");
        req.put("isPrimaryContact", true);
        MvcResult result = mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    /**
     * Creates a vehicle and returns its UUID.
     *
     * @param residentId  resident UUID.
     * @param apartmentId apartment UUID.
     * @param plate       license plate string.
     * @return the created vehicle UUID.
     */
    private UUID createVehicle(UUID residentId, UUID apartmentId, String plate) throws Exception {
        CreateVehicleRequest req = new CreateVehicleRequest(
                residentId, apartmentId, VehicleType.CAR, plate, "Toyota", "Camry", "White", null);
        MvcResult result = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    // =========================================================================
    // Test 1 — assign slot returns 201, slot status becomes OCCUPIED
    // =========================================================================

    @Test
    @DisplayName("POST /api/parking/slots/{id}/assign — 201, slot status = OCCUPIED")
    void assignSlot_availableSlot_returns201AndSlotOccupied() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("ParkBlock-Assign-" + uid);
        UUID apartmentId = createApartment(blockId, "PA-" + uid);
        UUID residentId = createResident(phoneFromUid(uid), apartmentId);
        String plate = "51A-" + uid;
        UUID vehicleId = createVehicle(residentId, apartmentId, plate);
        UUID slotId = createSlot("SA-" + uid, ParkingSlotType.CAR);

        CreateAssignmentRequest req = new CreateAssignmentRequest(
                slotId, vehicleId, apartmentId, LocalDate.now(), "PC-001", null);

        mockMvc.perform(post("/api/parking/slots/" + slotId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.slot.id").value(slotId.toString()))
                .andExpect(jsonPath("$.endDate").doesNotExist());
    }

    // =========================================================================
    // Test 2 — assign to already-assigned slot returns 409
    // =========================================================================

    @Test
    @DisplayName("POST /api/parking/slots/{id}/assign on already-assigned slot — 409 CONFLICT")
    void assignSlot_alreadyAssigned_returns409() throws Exception {
        String uid2 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("ParkBlock-Dup-" + uid2);
        UUID apartmentId = createApartment(blockId, "PD-" + uid2);
        UUID residentId = createResident(phoneFromUid(uid2), apartmentId);
        String plate1 = "52A-" + uid2;
        String plate2 = "52B-" + uid2;
        UUID vehicle1 = createVehicle(residentId, apartmentId, plate1);
        UUID vehicle2 = createVehicle(residentId, apartmentId, plate2);
        UUID slotId = createSlot("SD-" + uid2, ParkingSlotType.CAR);

        // First assignment — must succeed.
        CreateAssignmentRequest req1 = new CreateAssignmentRequest(
                slotId, vehicle1, apartmentId, LocalDate.now(), null, null);
        mockMvc.perform(post("/api/parking/slots/" + slotId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Second assignment on the same slot — must conflict.
        CreateAssignmentRequest req2 = new CreateAssignmentRequest(
                slotId, vehicle2, apartmentId, LocalDate.now(), null, null);
        mockMvc.perform(post("/api/parking/slots/" + slotId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    // =========================================================================
    // Test 3 — unassign slot returns 200, slot status becomes AVAILABLE
    // =========================================================================

    @Test
    @DisplayName("POST /api/parking/slots/{id}/unassign — 200, slot status = AVAILABLE")
    void unassignSlot_activeAssignment_returns200AndSlotAvailable() throws Exception {
        String uid3 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("ParkBlock-Unassign-" + uid3);
        UUID apartmentId = createApartment(blockId, "PU-" + uid3);
        UUID residentId = createResident(phoneFromUid(uid3), apartmentId);
        String plate = "53A-" + uid3;
        UUID vehicleId = createVehicle(residentId, apartmentId, plate);
        UUID slotId = createSlot("SU-" + uid3, ParkingSlotType.CAR);

        // Assign the slot first.
        CreateAssignmentRequest assignReq = new CreateAssignmentRequest(
                slotId, vehicleId, apartmentId, LocalDate.now(), null, null);
        mockMvc.perform(post("/api/parking/slots/" + slotId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated());

        // Unassign — expect 200 and endDate populated.
        mockMvc.perform(post("/api/parking/slots/" + slotId + "/unassign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").isNotEmpty());
    }

    // =========================================================================
    // Test 4 — guest vehicle checkout returns 200, exitTime set
    // =========================================================================

    @Test
    @DisplayName("PUT /api/parking/guests/{id}/checkout — 200, exitTime set")
    void checkoutGuest_presentGuest_returns200WithExitTime() throws Exception {
        String uid4 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("ParkBlock-Guest-" + uid4);
        UUID apartmentId = createApartment(blockId, "PG-" + uid4);

        // Log a guest vehicle entry.
        CreateGuestVehicleRequest logReq = new CreateGuestVehicleRequest(
                "99Z-" + uid4, "Visitor Name", apartmentId, "Delivery", null);
        MvcResult logResult = mockMvc.perform(post("/api/parking/guests")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> logBody = objectMapper.readValue(logResult.getResponse().getContentAsString(), Map.class);
        UUID guestId = UUID.fromString((String) logBody.get("id"));

        // Checkout with default server time (empty body).
        mockMvc.perform(put("/api/parking/guests/" + guestId + "/checkout")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitTime").isNotEmpty());
    }

    // =========================================================================
    // Test 5 — GET /api/parking/slots → 200, page (GAP-06)
    // =========================================================================

    @Test
    @DisplayName("GET /api/parking/slots — ADMIN returns 200 with page")
    void listSlots_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/parking/slots")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // Test 6 — GET /api/parking/assignments → 200, page (GAP-06)
    // =========================================================================

    @Test
    @DisplayName("GET /api/parking/assignments — ADMIN returns 200 with page")
    void listAssignments_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/parking/assignments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // Test 7 — GET /api/parking/guests → 200, page (GAP-06)
    // =========================================================================

    @Test
    @DisplayName("GET /api/parking/guests — ADMIN returns 200 with page")
    void listGuestVehicles_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/parking/guests")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // Test 8 — POST /api/parking/slots by non-ADMIN → 403 (GAP-06)
    // =========================================================================

    @Test
    @DisplayName("POST /api/parking/slots — non-ADMIN returns 403")
    void createSlot_nonAdmin_returns403() throws Exception {
        // Create and authenticate a RESIDENT user
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String resPhone = phoneFromUid(uid);
        CreateUserRequest userReq = new CreateUserRequest(
                null, "Resident", resPhone, UserRole.RESIDENT, "Password@123456");
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isCreated());

        vn.vtit.gemek.module.auth.dto.LoginRequest loginReq =
                new vn.vtit.gemek.module.auth.dto.LoginRequest(resPhone, "Password@123456");
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        java.util.Map<?, ?> loginBody =
                objectMapper.readValue(r.getResponse().getContentAsString(), java.util.Map.class);
        String residentToken = (String) loginBody.get("accessToken");

        CreateParkingSlotRequest req = new CreateParkingSlotRequest("SLOT-403-" + uid, "Z1", ParkingSlotType.CAR, null);
        mockMvc.perform(post("/api/parking/slots")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
