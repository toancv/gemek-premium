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
import vn.vtit.gemek.module.resident.dto.MoveOutRequest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ResidentController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>POST /api/residents now creates a new user + resident atomically. Tests cover the
 * transactional create (201, new user can login, dateOfBirth round-trips), duplicate-email
 * rollback (409, no orphan user), apartment-not-found rollback (404, no orphan user),
 * missing fields (400), and all read/update paths.
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
    private static final String DEFAULT_PASS   = "Resident@123456";

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
     * Creates a resident (and its backing user account) via the API.
     * Uses TENANT type and a standard password. Returns the resident UUID.
     *
     * @param email       the email for the new user account.
     * @param fullName    the full name for the new user account.
     * @param apartmentId the apartment UUID to assign.
     * @return the created resident UUID.
     */
    private UUID createResident(String email, String fullName, UUID apartmentId) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", fullName);
        req.put("email", email);
        req.put("password", DEFAULT_PASS);
        req.put("phone", "0900000001");
        req.put("dateOfBirth", "1990-01-01");
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

    private UUID createResident(String email, UUID apartmentId) throws Exception {
        return createResident(email, "Test Resident", apartmentId);
    }

    /**
     * Logs in with the given email and the standard resident password.
     *
     * @param email the user's email address.
     * @return the JWT access token string.
     */
    private String loginAs(String email) throws Exception {
        LoginRequest login = new LoginRequest(email, DEFAULT_PASS);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
    }

    // -------------------------------------------------------------------------
    // POST /api/residents — new transactional create tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/residents — creates user+resident atomically, 201; new user can login, has RESIDENT role and dateOfBirth")
    void createResident_transactional_201_userCanLogin() throws Exception {
        UUID blockId = createBlock("ResBlock-Trans-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T101");
        String email = "res.trans." + System.nanoTime() + "@test.com";

        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Tran Thi B");
        req.put("email", email);
        req.put("password", DEFAULT_PASS);
        req.put("phone", "0912345678");
        req.put("dateOfBirth", "1990-05-20");
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "OWNER");
        req.put("moveInDate", "2026-03-01");
        req.put("isPrimaryContact", true);

        // 1. Create resident → 201 with user and apartment embedded.
        MvcResult created = mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("OWNER"))
                .andExpect(jsonPath("$.isPrimaryContact").value(true))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.fullName").value("Tran Thi B"))
                .andExpect(jsonPath("$.user.dateOfBirth").value("1990-05-20"))
                .andExpect(jsonPath("$.apartment.id").value(apartmentId.toString()))
                .andReturn();

        // 2. New user can authenticate with the supplied password.
        LoginRequest login = new LoginRequest(email, DEFAULT_PASS);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        // 3. /auth/me returns role=RESIDENT.
        Map<?, ?> loginBody = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String newUserToken = (String) loginBody.get("accessToken");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + newUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESIDENT"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-20"));
    }

    @Test
    @DisplayName("POST /api/residents — duplicate email returns 409 and no orphan user is created")
    void createResident_duplicateEmail_409_noOrphanUser() throws Exception {
        UUID blockId = createBlock("ResBlock-DupEmail-" + System.nanoTime());
        UUID aptId1  = createApartment(blockId, "DE101");
        UUID aptId2  = createApartment(blockId, "DE102");
        String email = "res.dup." + System.nanoTime() + "@test.com";

        // First call succeeds.
        createResident(email, aptId1);

        // Second call with same email → 409.
        Map<String, Object> dup = new HashMap<>();
        dup.put("fullName", "Another Person");
        dup.put("email", email);
        dup.put("password", DEFAULT_PASS);
        dup.put("phone", "0900000002");
        dup.put("dateOfBirth", "1985-03-15");
        dup.put("apartmentId", aptId2.toString());
        dup.put("type", "TENANT");
        dup.put("moveInDate", "2026-04-01");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // Verify apt2 still has 0 active residents (no partial record created).
        mockMvc.perform(get("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", aptId2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("POST /api/residents — apartment not found returns 404 and no user is created")
    void createResident_apartmentNotFound_404_noUserCreated() throws Exception {
        String uniqueEmail = "res.aptnotfound." + System.nanoTime() + "@test.com";
        UUID bogusAptId = UUID.randomUUID();

        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Ghost User");
        req.put("email", uniqueEmail);
        req.put("password", DEFAULT_PASS);
        req.put("phone", "0900000003");
        req.put("dateOfBirth", "1992-07-20");
        req.put("apartmentId", bogusAptId.toString());
        req.put("type", "TENANT");
        req.put("moveInDate", "2026-01-01");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        // User must not exist — login attempt should fail (401).
        LoginRequest login = new LoginRequest(uniqueEmail, DEFAULT_PASS);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/residents — missing required fields returns 400")
    void createResident_missingRequiredFields_400() throws Exception {
        // Missing email, password, apartmentId, type, moveInDate.
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Incomplete");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/residents — weak password (no complexity) returns 400 VALIDATION_ERROR")
    void createResident_weakPassword_400() throws Exception {
        UUID blockId = createBlock("ResBlock-WeakPw-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "WP101");

        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Weak Pass User");
        req.put("email", "res.weakpw." + System.nanoTime() + "@test.com");
        req.put("password", "weak");   // non-blank but fails complexity rule
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "TENANT");
        req.put("moveInDate", "2026-01-01");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/residents — compliant password (upper+lower+digit+special, ≥8) returns 201")
    void createResident_compliantPassword_201() throws Exception {
        UUID blockId = createBlock("ResBlock-GoodPw-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "GP101");

        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Good Pass User");
        req.put("email", "res.goodpw." + System.nanoTime() + "@test.com");
        req.put("password", DEFAULT_PASS);   // "Resident@123456" — upper+lower+digit+special
        req.put("phone", "0900000004");
        req.put("dateOfBirth", "1991-06-15");
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "TENANT");
        req.put("moveInDate", "2026-01-01");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // POST /api/residents — admin creates with OWNER type (existing behaviour check)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/residents — ADMIN creates OWNER resident, returns 201")
    void createResident_adminRole_returns201() throws Exception {
        UUID blockId = createBlock("ResBlock-Create-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "R101");
        String email = "res.create." + System.nanoTime() + "@test.com";

        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "New Owner");
        req.put("email", email);
        req.put("password", DEFAULT_PASS);
        req.put("phone", "0900000005");
        req.put("dateOfBirth", "1980-11-20");
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "OWNER");
        req.put("moveInDate", "2026-03-01");
        req.put("isPrimaryContact", true);
        req.put("notes", "New owner");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("OWNER"))
                .andExpect(jsonPath("$.isPrimaryContact").value(true));
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
        UUID residentId = createResident(email, apartmentId);

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

        UUID residentId1 = createResident(email1, apartmentId1);
        createResident(email2, apartmentId2);

        String token2 = loginAs(email2);

        mockMvc.perform(get("/api/residents/" + residentId1)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // -------------------------------------------------------------------------
    // GET /api/residents/me
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/residents/me — active residency returns 200 with apartment")
    void getMyResident_activeResidency_returns200() throws Exception {
        UUID blockId = createBlock("ResBlock-Me-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "ME101");
        String email = "res.me." + System.nanoTime() + "@test.com";
        createResident(email, apartmentId);

        String residentToken = loginAs(email);

        mockMvc.perform(get("/api/residents/me")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apartment.id").value(apartmentId.toString()))
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("GET /api/residents/me — no active residency returns 404")
    void getMyResident_noActiveResidency_returns404() throws Exception {
        // Create a user via POST /api/users (NOT via residents endpoint) — has no residency.
        String email = "res.noactive." + System.nanoTime() + "@test.com";
        Map<String, Object> userReq = new HashMap<>();
        userReq.put("email", email);
        userReq.put("fullName", "No Active");
        userReq.put("phone", "0900000001");
        userReq.put("role", "RESIDENT");
        userReq.put("password", DEFAULT_PASS);
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isCreated());

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
        String email = "res.moveout." + System.nanoTime() + "@test.com";
        UUID residentId = createResident(email, apartmentId);

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
        createResident("res.listblock." + ts + "@test.com", "List Block User", aptId);

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
        createResident("res.srch1." + ts + "@test.com", uniqueTag + " Person", aptId1);
        createResident("res.srch2." + ts + "@test.com", "BetaOther Person", aptId2);

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
        createResident(email, "Email Search Person", aptId);

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
        UUID blockId = createBlock("ResBlock-Combo-" + ts);
        UUID aptId1  = createApartment(blockId, "CB101");
        UUID aptId2  = createApartment(blockId, "CB102");

        // OWNER
        Map<String, Object> ownerReq = new HashMap<>();
        ownerReq.put("fullName", uniqueTag + " Owner");
        ownerReq.put("email", "res.combo.owner." + ts + "@test.com");
        ownerReq.put("password", DEFAULT_PASS);
        ownerReq.put("phone", "0900000006");
        ownerReq.put("dateOfBirth", "1988-04-10");
        ownerReq.put("apartmentId", aptId1.toString());
        ownerReq.put("type", "OWNER");
        ownerReq.put("moveInDate", "2026-01-01");
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReq)))
                .andExpect(status().isCreated());

        // TENANT
        createResident("res.combo.tenant." + ts + "@test.com", uniqueTag + " Tenant", aptId2);

        mockMvc.perform(get("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", uniqueTag)
                        .param("type", "OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].type").value("OWNER"));
    }

    // -------------------------------------------------------------------------
    // POST /api/residents — phone and dateOfBirth required
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/residents — missing phone returns 400 VALIDATION_ERROR")
    void createResident_missingPhone_400() throws Exception {
        UUID blockId = createBlock("ResBlock-NoPhone-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "NP101");

        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "No Phone User");
        req.put("email", "res.nophone." + System.nanoTime() + "@test.com");
        req.put("password", DEFAULT_PASS);
        req.put("dateOfBirth", "1990-01-01");
        // phone omitted
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "TENANT");
        req.put("moveInDate", "2026-01-01");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/residents — missing dateOfBirth returns 400 VALIDATION_ERROR")
    void createResident_missingDateOfBirth_400() throws Exception {
        UUID blockId = createBlock("ResBlock-NoDob-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "ND101");

        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "No Dob User");
        req.put("email", "res.nodob." + System.nanoTime() + "@test.com");
        req.put("password", DEFAULT_PASS);
        req.put("phone", "0900000007");
        // dateOfBirth omitted
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "TENANT");
        req.put("moveInDate", "2026-01-01");

        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
