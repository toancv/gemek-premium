/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident;

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
import vn.vtit.gemek.module.resident.dto.MoveOutRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ResidentController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Covers: create resident (201), duplicate active resident (409), get own record as RESIDENT (200),
 * get another resident's record as RESIDENT (403), and record a move-out (200).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResidentControllerTest {

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
     * Creates a user with RESIDENT role and returns their UUID.
     *
     * @param email the user's email address.
     * @return the created user's UUID.
     */
    private UUID createResidentUser(String email) throws Exception {
        return createResidentUser(email, "Test Resident");
    }

    /**
     * Creates a user with RESIDENT role and the given full name, returns their UUID.
     *
     * @param email    the user's email address.
     * @param fullName the user's full name.
     * @return the created user's UUID.
     */
    private UUID createResidentUser(String email, String fullName) throws Exception {
        CreateUserRequest req = new CreateUserRequest(
                email, fullName, "0900000001", UserRole.RESIDENT, "Resident@123456");
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
     * Logs in as the given email with the standard resident password and returns the access token.
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
     * Creates a resident record via the API and returns its UUID.
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

    // -------------------------------------------------------------------------
    // POST /api/residents
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/residents — ADMIN creates resident, returns 201")
    void createResident_adminRole_returns201() throws Exception {
        UUID blockId = createBlock("ResBlock-Create-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "R101");
        UUID userId = createResidentUser("res.create." + System.nanoTime() + "@test.com");

        CreateResidentRequest req = new CreateResidentRequest(
                userId, apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 3, 1), true, "New owner");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("OWNER"))
                .andExpect(jsonPath("$.isPrimaryContact").value(true));
    }

    @Test
    @DisplayName("POST /api/residents — duplicate active resident returns 409 CONFLICT")
    void createResident_duplicateActive_returns409() throws Exception {
        UUID blockId = createBlock("ResBlock-Dup-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "D101");
        UUID apartmentId2 = createApartment(blockId, "D102");
        UUID userId = createResidentUser("res.dup." + System.nanoTime() + "@test.com");

        // First assignment succeeds.
        createResident(userId, apartmentId);

        // Second active assignment for the same user must conflict.
        CreateResidentRequest dup = new CreateResidentRequest(
                userId, apartmentId2, ResidentType.TENANT,
                LocalDate.of(2026, 4, 1), false, null);
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    // -------------------------------------------------------------------------
    // GET /api/residents/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/residents/{id} — RESIDENT reads own record, returns 200")
    void getResident_ownRecord_returns200() throws Exception {
        UUID blockId = createBlock("ResBlock-Own-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "O101");
        String email = "res.own." + System.nanoTime() + "@test.com";
        UUID userId = createResidentUser(email);
        UUID residentId = createResident(userId, apartmentId);

        String residentToken = loginAs(email);

        mockMvc.perform(get("/api/residents/" + residentId)
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(residentId.toString()));
    }

    @Test
    @DisplayName("GET /api/residents/{id} — RESIDENT accessing another record returns 403")
    void getResident_otherRecord_returns403() throws Exception {
        UUID blockId = createBlock("ResBlock-Other-" + System.nanoTime());
        UUID apartmentId1 = createApartment(blockId, "F101");
        UUID apartmentId2 = createApartment(blockId, "F102");

        String email1 = "res.other1." + System.nanoTime() + "@test.com";
        String email2 = "res.other2." + System.nanoTime() + "@test.com";
        UUID userId1 = createResidentUser(email1);
        UUID userId2 = createResidentUser(email2);

        // Create resident 1 and resident 2 in different apartments.
        UUID residentId1 = createResident(userId1, apartmentId1);
        createResident(userId2, apartmentId2);

        // Resident 2 attempts to access resident 1's record.
        String token2 = loginAs(email2);

        mockMvc.perform(get("/api/residents/" + residentId1)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // -------------------------------------------------------------------------
    // POST /api/residents/{id}/move-out
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // GET /api/residents/me
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/residents/me — active residency returns 200 with apartment")
    void getMyResident_activeResidency_returns200() throws Exception {
        UUID blockId = createBlock("ResBlock-Me-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "ME101");
        String email = "res.me." + System.nanoTime() + "@test.com";
        UUID userId = createResidentUser(email);
        createResident(userId, apartmentId);

        String residentToken = loginAs(email);

        mockMvc.perform(get("/api/residents/me")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apartment.id").value(apartmentId.toString()))
                .andExpect(jsonPath("$.user.id").value(userId.toString()));
    }

    @Test
    @DisplayName("GET /api/residents/me — no active residency returns 404")
    void getMyResident_noActiveResidency_returns404() throws Exception {
        String email = "res.noactive." + System.nanoTime() + "@test.com";
        createResidentUser(email);

        String residentToken = loginAs(email);

        mockMvc.perform(get("/api/residents/me")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // POST /api/residents/{id}/move-out
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/residents/{id}/move-out — ADMIN records move-out, returns 200")
    void moveOut_adminRole_returns200() throws Exception {
        UUID blockId = createBlock("ResBlock-MoveOut-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "M101");
        UUID userId = createResidentUser("res.moveout." + System.nanoTime() + "@test.com");
        UUID residentId = createResident(userId, apartmentId);

        MoveOutRequest req = new MoveOutRequest(LocalDate.of(2026, 6, 30), "Lease ended");

        mockMvc.perform(post("/api/residents/" + residentId + "/move-out")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moveOutDate").value("2026-06-30"));
    }

    // -------------------------------------------------------------------------
    // GET /api/residents — list + search + apartment.block
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/residents — no search returns 200 with apartment.block.name in response")
    void listResidents_noSearch_returns200WithBlock() throws Exception {
        long ts = System.nanoTime();
        String blockName = "ResBlock-ListBlock-" + ts;
        UUID blockId = createBlock(blockName);
        UUID aptId   = createApartment(blockId, "LB101");
        UUID userId  = createResidentUser("res.listblock." + ts + "@test.com", "List Block User");
        createResident(userId, aptId);

        mockMvc.perform(get("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", aptId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].apartment.unitNumber").value("LB101"))
                .andExpect(jsonPath("$.data[0].apartment.block.name").value(blockName));
    }

    @Test
    @DisplayName("GET /api/residents?search=<name> — returns residents whose name matches, 200")
    void listResidents_searchByName_returnsMatching() throws Exception {
        long ts = System.nanoTime();
        UUID blockId = createBlock("ResBlock-SrchName-" + ts);
        UUID aptId1  = createApartment(blockId, "SN101");
        UUID aptId2  = createApartment(blockId, "SN102");

        String uniqueTag = "AlphaUniq" + ts;
        UUID userId1 = createResidentUser("res.srch1." + ts + "@test.com", uniqueTag + " Person");
        UUID userId2 = createResidentUser("res.srch2." + ts + "@test.com", "BetaOther Person");
        createResident(userId1, aptId1);
        createResident(userId2, aptId2);

        mockMvc.perform(get("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", uniqueTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].user.fullName").value(uniqueTag + " Person"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("GET /api/residents?search=<email> — matches by email substring, 200")
    void listResidents_searchByEmail_returnsMatching() throws Exception {
        long ts = System.nanoTime();
        String uniquePart = "emailsrch" + ts;
        String email = "res." + uniquePart + "@test.com";
        UUID blockId = createBlock("ResBlock-SrchEmail-" + ts);
        UUID aptId   = createApartment(blockId, "SE101");
        UUID userId  = createResidentUser(email, "Email Search Person");
        createResident(userId, aptId);

        mockMvc.perform(get("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", uniquePart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].user.email").value(email))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("GET /api/residents?search= — blank search returns 200 (no crash)")
    void listResidents_blankSearch_returns200() throws Exception {
        mockMvc.perform(get("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", ""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/residents?search=X&type=OWNER — search combined with type filter, 200")
    void listResidents_searchWithTypeFilter_returnsOnlyMatchingType() throws Exception {
        long ts = System.nanoTime();
        String uniqueTag = "ComboTag" + ts;
        UUID blockId    = createBlock("ResBlock-Combo-" + ts);
        UUID aptId1     = createApartment(blockId, "CB101");
        UUID aptId2     = createApartment(blockId, "CB102");
        UUID ownerUser  = createResidentUser("res.combo.owner." + ts + "@test.com", uniqueTag + " Owner");
        UUID tenantUser = createResidentUser("res.combo.tenant." + ts + "@test.com", uniqueTag + " Tenant");

        CreateResidentRequest ownerReq = new CreateResidentRequest(
                ownerUser, aptId1, ResidentType.OWNER, LocalDate.of(2026, 1, 1), false, null);
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReq)))
                .andExpect(status().isCreated());
        createResident(tenantUser, aptId2);

        mockMvc.perform(get("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", uniqueTag)
                        .param("type", "OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].type").value("OWNER"));
    }
}
