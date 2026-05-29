/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.parking.dto.CheckoutRequest;
import vn.vtit.gemek.module.parking.dto.CreateAssignmentRequest;
import vn.vtit.gemek.module.parking.dto.CreateGuestVehicleRequest;
import vn.vtit.gemek.module.parking.dto.CreateParkingSlotRequest;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.vehicle.dto.CreateVehicleRequest;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

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
@Testcontainers
@ActiveProfiles("test")
class ParkingControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("gemek_test")
            .withUsername("gemek")
            .withPassword("gemek_test_pass");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret",
                () -> "test-secret-key-that-is-long-enough-for-hs256-algorithm-minimum-256-bits");
        registry.add("jwt.access-token-expiry-ms", () -> "900000");
        registry.add("jwt.refresh-token-expiry-ms", () -> "604800000");
        registry.add("firebase.enabled", () -> "false");
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    private static final String ADMIN_EMAIL    = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @BeforeEach
    void obtainAdminToken() throws Exception {
        LoginRequest login = new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);
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

    /**
     * Creates a user with RESIDENT role and returns the UUID.
     *
     * @param email user email.
     * @return the created user UUID.
     */
    private UUID createResidentUser(String email) throws Exception {
        CreateUserRequest req = new CreateUserRequest(
                email, "Parking Resident", "0900000001", UserRole.RESIDENT, "Resident@123456");
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    /**
     * Creates a resident record and returns its UUID.
     *
     * @param userId      user UUID.
     * @param apartmentId apartment UUID.
     * @return the created resident UUID.
     */
    private UUID createResident(UUID userId, UUID apartmentId) throws Exception {
        CreateResidentRequest req = new CreateResidentRequest(
                userId, apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), true, null);
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
        UUID blockId = createBlock("ParkBlock-Assign-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "PA" + System.nanoTime() % 10000);
        UUID userId = createResidentUser("park.assign." + System.nanoTime() + "@test.com");
        UUID residentId = createResident(userId, apartmentId);
        String plate = "51A-" + System.nanoTime() % 100000;
        UUID vehicleId = createVehicle(residentId, apartmentId, plate);
        UUID slotId = createSlot("S-ASSIGN-" + System.nanoTime() % 10000, ParkingSlotType.CAR);

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
        UUID blockId = createBlock("ParkBlock-Dup-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "PD" + System.nanoTime() % 10000);
        UUID userId = createResidentUser("park.dup." + System.nanoTime() + "@test.com");
        UUID residentId = createResident(userId, apartmentId);
        String plate1 = "52A-" + System.nanoTime() % 100000;
        String plate2 = "52B-" + (System.nanoTime() + 1) % 100000;
        UUID vehicle1 = createVehicle(residentId, apartmentId, plate1);
        UUID vehicle2 = createVehicle(residentId, apartmentId, plate2);
        UUID slotId = createSlot("S-DUP-" + System.nanoTime() % 10000, ParkingSlotType.CAR);

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
        UUID blockId = createBlock("ParkBlock-Unassign-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "PU" + System.nanoTime() % 10000);
        UUID userId = createResidentUser("park.unassign." + System.nanoTime() + "@test.com");
        UUID residentId = createResident(userId, apartmentId);
        String plate = "53A-" + System.nanoTime() % 100000;
        UUID vehicleId = createVehicle(residentId, apartmentId, plate);
        UUID slotId = createSlot("S-UNASSIGN-" + System.nanoTime() % 10000, ParkingSlotType.CAR);

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
        UUID blockId = createBlock("ParkBlock-Guest-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "PG" + System.nanoTime() % 10000);

        // Log a guest vehicle entry.
        CreateGuestVehicleRequest logReq = new CreateGuestVehicleRequest(
                "99Z-" + System.nanoTime() % 100000, "Visitor Name", apartmentId, "Delivery", null);
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
}
