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
import java.util.HashMap;
import vn.vtit.gemek.module.vehicle.dto.CreateVehicleRequest;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

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
     * Creates a new user+resident atomically via the new transactional endpoint.
     *
     * @param email       the new user's email.
     * @param apartmentId the apartment UUID to assign.
     * @return the created resident UUID.
     */
    private UUID createResident(String email, UUID apartmentId) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Vehicle Resident");
        req.put("email", email);
        req.put("password", "Resident@123456");
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "TENANT");
        req.put("moveInDate", "2026-01-01");
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
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("VehBlock-Create-" + uid);
        UUID apartmentId = createApartment(blockId, "V1-" + uid);
        UUID residentId = createResident("veh.create." + uid + "@test.com", apartmentId);

        String plate = "51A-" + uid;
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
        String uid2 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("VehBlock-Dup-" + uid2);
        UUID apartmentId = createApartment(blockId, "V2-" + uid2);
        UUID residentId = createResident("veh.dup." + uid2 + "@test.com", apartmentId);

        String plate = "51B-" + uid2;
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
        String uid3 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("VehBlock-Own-" + uid3);
        UUID apartmentId = createApartment(blockId, "V3-" + uid3);
        String email = "veh.own." + uid3 + "@test.com";
        UUID residentId = createResident(email, apartmentId);

        String plate = "51C-" + uid3;
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
        String uid4 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("VehBlock-Other-" + uid4);
        UUID aptA = createApartment(blockId, "V4A-" + uid4);
        UUID aptB = createApartment(blockId, "V4B-" + uid4);

        String emailA = "veh.otherA." + uid4 + "@test.com";
        String emailB = "veh.otherB." + uid4 + "@test.com";
        UUID residentA = createResident(emailA, aptA);
        createResident(emailB, aptB);

        // Vehicle registered to apartment A.
        String plate = "51D-" + uid4;
        UUID vehicleId = createVehicle(residentA, aptA, plate);

        // Resident B (in apartment B) tries to read vehicle from apartment A.
        String tokenB = loginAs(emailB);

        mockMvc.perform(get("/api/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // -------------------------------------------------------------------------
    // GET /api/vehicles — search param
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/vehicles — no search param returns 200")
    void listVehicles_noSearch_returns200() throws Exception {
        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/vehicles?search= — blank search returns 200, no 500")
    void listVehicles_blankSearch_returns200() throws Exception {
        mockMvc.perform(get("/api/vehicles").param("search", "")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/vehicles?search=<plate-substring> — filters by license plate")
    void listVehicles_searchByPlateSubstring_filtersResults() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId    = createBlock("VehBlock-Srch-" + uid);
        UUID apartmentId = createApartment(blockId, "VS1-" + uid);
        UUID residentId = createResident("veh.srch." + uid + "@test.com", apartmentId);

        // Plate chosen to be unique enough for substring match.
        String plate = "SRCH" + uid;
        createVehicle(residentId, apartmentId, plate);

        // Matching search — exactly this vehicle must be returned.
        mockMvc.perform(get("/api/vehicles").param("search", "SRCH" + uid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].licensePlate").value(plate));

        // Non-matching search — vehicle must not appear.
        mockMvc.perform(get("/api/vehicles").param("search", "NOMATCH-" + uid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /api/vehicles?search=<term>&apartmentId=<id> — search combines with apartmentId filter")
    void listVehicles_searchWithApartmentId_combinesFilters() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("VehBlock-Flt-" + uid);
        UUID aptA    = createApartment(blockId, "VFA-" + uid);
        UUID aptB    = createApartment(blockId, "VFB-" + uid);

        UUID resA  = createResident("veh.fltA." + uid + "@test.com", aptA);
        UUID resB  = createResident("veh.fltB." + uid + "@test.com", aptB);

        // Both plates share the same uid substring so both match the search term.
        String plateA = "FLT" + uid + "A";
        String plateB = "FLT" + uid + "B";
        createVehicle(resA, aptA, plateA);
        createVehicle(resB, aptB, plateB);

        // search=FLT{uid} + apartmentId=aptA → only vehicle from aptA.
        mockMvc.perform(get("/api/vehicles")
                        .param("search", "FLT" + uid)
                        .param("apartmentId", aptA.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].licensePlate").value(plateA));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/vehicles/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/vehicles/{id} — ADMIN soft-deletes vehicle, returns 204")
    void deleteVehicle_adminRole_returns204() throws Exception {
        String uid5 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId = createBlock("VehBlock-Del-" + uid5);
        UUID apartmentId = createApartment(blockId, "V5-" + uid5);
        UUID residentId = createResident("veh.del." + uid5 + "@test.com", apartmentId);

        String plate = "51E-" + uid5;
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
