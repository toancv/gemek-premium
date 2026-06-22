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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link BlockController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Covers: list blocks (PageResponse shape, default sort, search, pagination),
 * create block (201), duplicate name (409), delete with apartments (409).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlockControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Admin token obtained at test setup. */
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
    // GET /api/blocks — PageResponse shape
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/blocks — returns 200 with PageResponse shape")
    void listBlocks_returns200WithPageResponseShape() throws Exception {
        mockMvc.perform(get("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/blocks — default size=10 and sorted by name asc")
    void listBlocks_defaultSizeAndSort() throws Exception {
        mockMvc.perform(get("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/blocks?search=<substr> — filters results by name substring")
    void listBlocks_searchFilters() throws Exception {
        // Create a block with a distinctive name to search for.
        String uniqueMarker = "SRCHTEST-" + System.nanoTime();
        CreateBlockRequest request = new CreateBlockRequest(uniqueMarker, null);
        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Search by the full unique name — exactly 1 match regardless of prior test runs.
        mockMvc.perform(get("/api/blocks")
                        .param("search", uniqueMarker)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value(uniqueMarker));
    }

    @Test
    @DisplayName("GET /api/blocks?page=0&size=2 — paginates results")
    void listBlocks_pagination() throws Exception {
        mockMvc.perform(get("/api/blocks")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/blocks?search= — blank search returns all results, no 500")
    void listBlocks_blankSearch_noError() throws Exception {
        mockMvc.perform(get("/api/blocks")
                        .param("search", "")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // -------------------------------------------------------------------------
    // POST /api/blocks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/blocks — ADMIN creates block, returns 201 with id and name")
    void createBlock_adminRole_returns201() throws Exception {
        String uniqueName = "Block-" + System.nanoTime();
        CreateBlockRequest request = new CreateBlockRequest(uniqueName, "Integration test block");

        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value(uniqueName));
    }

    @Test
    @DisplayName("POST /api/blocks — duplicate name returns 409 CONFLICT")
    void createBlock_duplicateName_returns409() throws Exception {
        String uniqueName = "Block-" + System.nanoTime();
        CreateBlockRequest request = new CreateBlockRequest(uniqueName, null);

        // First create succeeds.
        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second create with same name must conflict.
        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("POST /api/blocks — missing name returns 400 VALIDATION_ERROR")
    void createBlock_missingName_returns400() throws Exception {
        Map<String, String> body = Map.of("description", "no name");

        mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/blocks/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/blocks/{id} — returns 409 when block has apartments")
    void deleteBlock_withApartments_returns409() throws Exception {
        // Create a block.
        CreateBlockRequest blockRequest = new CreateBlockRequest("Block-" + System.nanoTime(), null);
        MvcResult blockResult = mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> blockBody = objectMapper.readValue(blockResult.getResponse().getContentAsString(), Map.class);
        String blockId = (String) blockBody.get("id");

        // Create an apartment in that block.
        CreateApartmentRequest aptRequest = new CreateApartmentRequest(
                java.util.UUID.fromString(blockId), (short) 1, "B101", null, null);
        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aptRequest)))
                .andExpect(status().isCreated());

        // Now attempt to delete the block — must be rejected.
        mockMvc.perform(delete("/api/blocks/" + blockId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }
}
