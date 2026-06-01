/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle;

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
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.vehicle.dto.CreateVehicleRequest;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link VehicleController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Covers: ADMIN registers vehicle (201), duplicate license plate (409),
 * RESIDENT reads vehicle in own apartment (200), RESIDENT accessing vehicle in another
 * apartment (403), and ADMIN soft-deletes vehicle (204).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleControllerTest {

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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
     * Creates a RESIDENT user, returns their UUID.
     *
     * @param email the user's email address.
     * @return the created user's UUID.
     */
    private UUID createResidentUser(String email) throws Exception {
        CreateUserRequest req = new CreateUserRequest(
                email, "Vehicle Resident", "0911111111", UserRole.RESIDENT, "Resident@123456");
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
     * Logs in with the given email and standard resident password, returns the access token.
     *
     * @param email the user's email address.
     * @return the JWT access token string.
     */
    private String loginAs(String email) throws Exception {
        LoginRequest login = new LoginRequest(email, "Resident@123456");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
    }

    /**
     * Creates a resident record and returns its UUID.
     *
     * @param userId      the user UUID.
     * @param apartmentId the apartment UUID.
     * @return the created resident UUID.
     */
    private UUID createResident(UUID userId, UUID apartmentId) throws Exception {
        CreateResidentRequest req = new CreateResidentRequest(
                userId, apartmentId, ResidentType.TENANT,
                LocalDate.of(2026, 1, 1), false, null);
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
     * Creates a vehicle record via ADMIN token and returns its UUID.
     *
     * @param residentId  the resident UUID.
     * @param apartmentId the apartment UUID.
     * @param plate       the license plate string.
     * @return the created vehicle UUID.
     */
    private UUID createVehicle(UUID residentId, UUID apartmentId, String plate) throws Exception {
        CreateVehicleRequest req = new CreateVehicleRequest(
                residentId, apartmentId, VehicleType.MOTORBIKE, plate, "Honda", "Wave", "Red", null);
        MvcResult result = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    // -------------------------------------------------------------------------
    // POST /api/vehicles
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/vehicles — ADMIN registers vehicle, returns 201")
    void createVehicle_adminRole_returns201() throws Exception {
        UUID blockId = createBlock("VehBlock-Create-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "V101");
        UUID userId = createResidentUser("veh.create." + System.nanoTime() + "@test.com");
        UUID residentId = createResident(userId, apartmentId);

        String plate = "51A-" + System.nanoTime() % 100000;
        CreateVehicleRequest req = new CreateVehicleRequest(
                residentId, apartmentId, VehicleType.CAR, plate, "Toyota", "Camry", "White", null);

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.licensePlate").value(plate))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("POST /api/vehicles — duplicate license plate returns 409 CONFLICT")
    void createVehicle_duplicatePlate_returns409() throws Exception {
        UUID blockId = createBlock("VehBlock-Dup-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "V201");
        UUID userId = createResidentUser("veh.dup." + System.nanoTime() + "@test.com");
        UUID residentId = createResident(userId, apartmentId);

        String plate = "51B-DUP" + System.nanoTime() % 10000;
        createVehicle(residentId, apartmentId, plate);

        // Second vehicle with same plate must conflict.
        CreateVehicleRequest dup = new CreateVehicleRequest(
                residentId, apartmentId, VehicleType.MOTORBIKE, plate, null, null, null, null);
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    // -------------------------------------------------------------------------
    // GET /api/vehicles/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/vehicles/{id} — RESIDENT reads vehicle in own apartment, returns 200")
    void getVehicle_residentOwnApartment_returns200() throws Exception {
        UUID blockId = createBlock("VehBlock-Own-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "V301");
        String email = "veh.own." + System.nanoTime() + "@test.com";
        UUID userId = createResidentUser(email);
        UUID residentId = createResident(userId, apartmentId);

        String plate = "51C-" + System.nanoTime() % 100000;
        UUID vehicleId = createVehicle(residentId, apartmentId, plate);

        String residentToken = loginAs(email);

        mockMvc.perform(get("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId.toString()));
    }

    @Test
    @DisplayName("GET /api/vehicles/{id} — RESIDENT accessing vehicle in another apartment returns 403")
    void getVehicle_residentOtherApartment_returns403() throws Exception {
        UUID blockId = createBlock("VehBlock-Other-" + System.nanoTime());
        UUID aptA = createApartment(blockId, "V401");
        UUID aptB = createApartment(blockId, "V402");

        String emailA = "veh.otherA." + System.nanoTime() + "@test.com";
        String emailB = "veh.otherB." + System.nanoTime() + "@test.com";
        UUID userA = createResidentUser(emailA);
        UUID userB = createResidentUser(emailB);

        UUID residentA = createResident(userA, aptA);
        createResident(userB, aptB);

        // Vehicle registered to apartment A.
        String plate = "51D-" + System.nanoTime() % 100000;
        UUID vehicleId = createVehicle(residentA, aptA, plate);

        // Resident B (in apartment B) tries to read vehicle from apartment A.
        String tokenB = loginAs(emailB);

        mockMvc.perform(get("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/vehicles/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/vehicles/{id} — ADMIN soft-deletes vehicle, returns 204")
    void deleteVehicle_adminRole_returns204() throws Exception {
        UUID blockId = createBlock("VehBlock-Del-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "V501");
        UUID userId = createResidentUser("veh.del." + System.nanoTime() + "@test.com");
        UUID residentId = createResident(userId, apartmentId);

        String plate = "51E-" + System.nanoTime() % 100000;
        UUID vehicleId = createVehicle(residentId, apartmentId, plate);

        mockMvc.perform(delete("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify the record is soft-deleted (isActive = false), not removed.
        mockMvc.perform(get("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }
}
