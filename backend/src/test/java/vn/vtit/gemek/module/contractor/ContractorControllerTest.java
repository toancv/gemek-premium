/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;
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
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractPaymentRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractorRequest;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ContractorController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Covers: create contractor (201), list contractors (200 paginated),
 * create contract under contractor (201), record payment (201), and
 * soft-delete contractor (204 with active=false).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContractorControllerTest extends AbstractIntegrationTest {

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
    // Helper: create a contractor and return its UUID.
    // -------------------------------------------------------------------------

    /**
     * Creates a contractor with a unique company name and returns its UUID.
     *
     * @param suffix unique suffix appended to the company name to avoid conflicts.
     * @return the created contractor UUID.
     */
    private UUID createContractor(String suffix) throws Exception {
        CreateContractorRequest req = new CreateContractorRequest(
                "Test Co " + suffix,
                "Contact Person",
                "0900000001",
                "contact@testco.vn",
                "123 Main St",
                ContractorSpecialty.ELECTRICAL,
                "TAX-" + suffix,
                "Test notes");
        MvcResult result = mockMvc.perform(post("/api/contractors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    // -------------------------------------------------------------------------
    // Helper: create a contract under a contractor and return its UUID.
    // -------------------------------------------------------------------------

    /**
     * Creates a contract under the given contractor and returns its UUID.
     *
     * @param contractorId the parent contractor UUID.
     * @return the created contract UUID.
     */
    private UUID createContract(UUID contractorId) throws Exception {
        CreateContractRequest req = new CreateContractRequest(
                contractorId,
                "Electrical Maintenance Contract",
                "Annual electrical systems check",
                new BigDecimal("50000000"),
                "VND",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "Standard annual contract");
        MvcResult result = mockMvc.perform(post("/api/contractors/" + contractorId + "/contracts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    // =========================================================================
    // Test 1 — POST /api/contractors → 201
    // =========================================================================

    @Test
    @DisplayName("POST /api/contractors — ADMIN creates contractor, returns 201")
    void createContractor_adminRole_returns201() throws Exception {
        CreateContractorRequest request = new CreateContractorRequest(
                "Gemek Electrical Ltd " + System.nanoTime(),
                "Nguyen Van A",
                "0901234567",
                "info@gemek-elec.vn",
                "456 Industrial Zone, HCM",
                ContractorSpecialty.ELECTRICAL,
                "TAX-GEL-001",
                "Preferred electrical contractor");

        mockMvc.perform(post("/api/contractors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.companyName").value(request.companyName()))
                .andExpect(jsonPath("$.specialty").value("ELECTRICAL"))
                .andExpect(jsonPath("$.active").value(true));
    }

    // =========================================================================
    // Test 2 — GET /api/contractors → 200 paginated
    // =========================================================================

    @Test
    @DisplayName("GET /api/contractors — returns 200 paginated list")
    void listContractors_returns200Paginated() throws Exception {
        // Create two contractors to ensure the list is non-empty.
        createContractor("LIST-A-" + System.nanoTime());
        createContractor("LIST-B-" + System.nanoTime());

        mockMvc.perform(get("/api/contractors")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.total").isNumber());
    }

    // =========================================================================
    // Test 3 — POST /api/contractors/{id}/contracts → 201
    // =========================================================================

    @Test
    @DisplayName("POST /api/contractors/{id}/contracts — ADMIN creates contract, returns 201")
    void createContract_adminRole_returns201() throws Exception {
        UUID contractorId = createContractor("CONTRACT-" + System.nanoTime());

        CreateContractRequest request = new CreateContractRequest(
                contractorId,
                "Annual Electrical Service",
                "Inspection and maintenance of all electrical installations",
                new BigDecimal("120000000"),
                "VND",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "Includes quarterly visits");

        mockMvc.perform(post("/api/contractors/" + contractorId + "/contracts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Annual Electrical Service"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.contractor.id").value(contractorId.toString()));
    }

    // =========================================================================
    // Test 4 — POST /api/contracts/{id}/payments → 201
    // =========================================================================

    @Test
    @DisplayName("POST /api/contracts/{id}/payments — ADMIN records payment, returns 201")
    void addPayment_adminRole_returns201() throws Exception {
        UUID contractorId = createContractor("PAY-" + System.nanoTime());
        UUID contractId = createContract(contractorId);

        CreateContractPaymentRequest request = new CreateContractPaymentRequest(
                new BigDecimal("10000000"),
                LocalDate.of(2026, 2, 15),
                "First instalment payment",
                "BANK-TXN-001");

        mockMvc.perform(post("/api/contracts/" + contractId + "/payments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(10000000))
                .andExpect(jsonPath("$.referenceNumber").value("BANK-TXN-001"))
                .andExpect(jsonPath("$.contract.id").value(contractId.toString()));
    }

    // =========================================================================
    // Test 5 — DELETE /api/contractors/{id} → 204, active=false
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/contractors/{id} — ADMIN soft-deletes contractor, returns 204 and active becomes false")
    void deactivateContractor_adminRole_returns204AndActiveIsFalse() throws Exception {
        UUID contractorId = createContractor("DEL-" + System.nanoTime());

        // Soft-delete.
        mockMvc.perform(delete("/api/contractors/" + contractorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify the contractor is still retrievable but active=false.
        mockMvc.perform(get("/api/contractors/" + contractorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contractorId.toString()))
                .andExpect(jsonPath("$.active").value(false));
    }
}
