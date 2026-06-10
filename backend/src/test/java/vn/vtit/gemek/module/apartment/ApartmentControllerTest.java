/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

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
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ApartmentController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Covers: create apartment (201), duplicate unit in block (409), paginated list with
 * blockId filter (200), GET detail as ADMIN (200), and GET detail as RESIDENT accessing
 * another apartment (403).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApartmentControllerTest {

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

    // -------------------------------------------------------------------------
    // Helper: create a block and return its UUID.
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

    // -------------------------------------------------------------------------
    // Helper: create an apartment and return its UUID.
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Helper: create a RESIDENT user and return their access token.
    // -------------------------------------------------------------------------

    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

    private String createResidentAndLogin(String phone) throws Exception {
        CreateUserRequest createReq = new CreateUserRequest(
                null, "Test Resident", phone, UserRole.RESIDENT, "Resident@123456");
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(phone, "Resident@123456");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
    }

    // -------------------------------------------------------------------------
    // POST /api/apartments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/apartments — ADMIN creates apartment, returns 201")
    void createApartment_adminRole_returns201() throws Exception {
        UUID blockId = createBlock("Block-Apt-Create-" + System.nanoTime());

        CreateApartmentRequest request = new CreateApartmentRequest(blockId, (short) 3, "C301", null, null);

        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.unitNumber").value("C301"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/apartments — duplicate unit in block returns 409 CONFLICT")
    void createApartment_duplicateUnit_returns409() throws Exception {
        UUID blockId = createBlock("Block-Dup-Apt-" + System.nanoTime());

        // First apartment succeeds.
        createApartment(blockId, "DUP-101");

        // Second apartment with same unit in same block must conflict.
        CreateApartmentRequest duplicate = new CreateApartmentRequest(blockId, (short) 1, "DUP-101", null, null);
        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    // -------------------------------------------------------------------------
    // GET /api/apartments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/apartments — returns 200 paginated with blockId filter")
    void listApartments_withBlockIdFilter_returns200() throws Exception {
        UUID blockId = createBlock("Block-List-Filter-" + System.nanoTime());
        createApartment(blockId, "L101");
        createApartment(blockId, "L102");

        mockMvc.perform(get("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("blockId", blockId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[0].block.id").value(blockId.toString()));
    }

    // -------------------------------------------------------------------------
    // GET /api/apartments/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/apartments/{id} — ADMIN returns 200 with detail")
    void getApartmentDetail_adminRole_returns200() throws Exception {
        UUID blockId = createBlock("Block-Detail-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "D501");

        mockMvc.perform(get("/api/apartments/" + apartmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(apartmentId.toString()))
                .andExpect(jsonPath("$.unitNumber").value("D501"))
                .andExpect(jsonPath("$.residents").isArray())
                .andExpect(jsonPath("$.vehicles").isArray());
    }

    @Test
    @DisplayName("GET /api/apartments/{id} — RESIDENT accessing another apartment returns 403")
    void getApartmentDetail_residentOtherApartment_returns403() throws Exception {
        UUID blockId = createBlock("Block-Resident-403-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "R901");

        // Create a RESIDENT user who is not assigned to any apartment.
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String residentToken = createResidentAndLogin(phoneFromUid(uid));

        mockMvc.perform(get("/api/apartments/" + apartmentId)
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
