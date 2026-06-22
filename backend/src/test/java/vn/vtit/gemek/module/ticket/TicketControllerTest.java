/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import java.util.HashMap;
import vn.vtit.gemek.module.ticket.dto.AssignTicketRequest;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.UpdateTicketStatusRequest;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link TicketController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>{@link FileStorageService} is mocked because MinIO is not started in this test suite.
 * The 8 tests cover: ticket creation, assignment, contractor restriction, status transitions,
 * rating lifecycle, and resident scoping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketControllerTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String technicianToken;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    @BeforeEach
    void setUp() throws Exception {
        // Presign mock returns a fixed URL for any key.
        when(fileStorageService.presign(anyString())).thenReturn("http://minio/presigned-url");

        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

    /**
     * Logs in and returns the access token.
     *
     * @param phone    user phone number.
     * @param password user password.
     * @return the JWT access token string.
     */
    private String login(String phone, String password) throws Exception {
        LoginRequest req = new LoginRequest(phone, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
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
     * @param blockId    the parent block UUID.
     * @param unitNumber the unit number string.
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
     * Creates a user with the given role and returns their UUID.
     *
     * @param phone the user phone number.
     * @param role  the user role.
     * @return the created user UUID.
     */
    private UUID createUser(String phone, UserRole role) throws Exception {
        CreateUserRequest req = new CreateUserRequest(
                null, "Test User", phone, role, "Password@123456");
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
     * Assigns a user as an active resident of the given apartment.
     *
     * @param phone       the new user's phone number.
     * @param apartmentId the apartment UUID.
     */
    private void assignResident(String phone, UUID apartmentId) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Test Resident");
        req.put("phone", phone);
        req.put("dateOfBirth", "1990-01-01");
        req.put("password", "Password@123456");
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "OWNER");
        req.put("moveInDate", "2026-01-01");
        req.put("isPrimaryContact", true);
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    /**
     * Creates a ticket as the given user token and returns the ticket UUID.
     *
     * @param token       the caller's JWT token.
     * @param apartmentId the apartment UUID.
     * @param category    the ticket category.
     * @return the created ticket UUID.
     */
    private UUID createTicket(String token, UUID apartmentId, TicketCategory category) throws Exception {
        CreateTicketRequest req = CreateTicketRequest.builder()
                .apartmentId(apartmentId)
                .category(category)
                .title("Test ticket " + System.nanoTime())
                .priority(TicketPriority.MEDIUM)
                .build();
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    // =========================================================================
    // Test 1 — POST /api/tickets (RESIDENT) → 201, status=NEW
    // =========================================================================

    @Test
    @DisplayName("POST /api/tickets — RESIDENT submits ticket for own apartment, returns 201 with status NEW")
    void createTicket_resident_returns201StatusNew() throws Exception {
        UUID blockId = createBlock("TBlock1-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T101");
        String uid1 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phone1 = phoneFromUid(uid1);
        assignResident(phone1, apartmentId);
        String residentToken = login(phone1, "Password@123456");

        CreateTicketRequest req = CreateTicketRequest.builder()
                .apartmentId(apartmentId)
                .category(TicketCategory.COMPLAINT)
                .title("Noisy neighbours")
                .priority(TicketPriority.HIGH)
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.category").value("COMPLAINT"));
    }

    // =========================================================================
    // Test 2 — PUT /api/tickets/{id}/assign — assign to staff → 200, status=ASSIGNED
    // =========================================================================

    @Test
    @DisplayName("PUT /api/tickets/{id}/assign — ADMIN assigns to staff, returns 200 with status ASSIGNED")
    void assignTicket_toStaff_returns200StatusAssigned() throws Exception {
        UUID blockId = createBlock("TBlock2-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T201");
        UUID ticketId = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        String techUid2 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId = createUser(phoneFromUid(techUid2), UserRole.TECHNICIAN);

        AssignTicketRequest req = AssignTicketRequest.builder()
                .assignedToUserId(techId)
                .scheduledDate(LocalDate.of(2026, 6, 15))
                .notes("Assigned to tech")
                .build();

        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedToUser").isNotEmpty());
    }

    // =========================================================================
    // Test 3 — PUT /api/tickets/{id}/assign — contractor on COMPLAINT → 400
    // =========================================================================

    @Test
    @DisplayName("PUT /api/tickets/{id}/assign — assigning contractor to COMPLAINT ticket returns 400 CONTRACTOR_ASSIGNMENT_NOT_ALLOWED")
    void assignTicket_contractorToComplaint_returns400() throws Exception {
        UUID blockId = createBlock("TBlock3-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T301");
        UUID ticketId = createTicket(adminToken, apartmentId, TicketCategory.COMPLAINT);

        // Use a random UUID for contractor — the business rule check happens before the DB lookup.
        AssignTicketRequest req = AssignTicketRequest.builder()
                .assignedToContractorId(UUID.randomUUID())
                .build();

        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTRACTOR_ASSIGNMENT_NOT_ALLOWED"));
    }

    // =========================================================================
    // Test 4 — PUT /api/tickets/{id}/status — ASSIGNED → IN_PROGRESS → 200
    // =========================================================================

    @Test
    @DisplayName("PUT /api/tickets/{id}/status — ASSIGNED to IN_PROGRESS returns 200")
    void updateStatus_assignedToInProgress_returns200() throws Exception {
        UUID blockId = createBlock("TBlock4-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T401");
        UUID ticketId = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        String techUid4 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId = createUser(phoneFromUid(techUid4), UserRole.TECHNICIAN);

        // Assign the ticket first.
        AssignTicketRequest assignReq = AssignTicketRequest.builder()
                .assignedToUserId(techId)
                .build();
        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk());

        // Now move to IN_PROGRESS as admin.
        UpdateTicketStatusRequest statusReq = UpdateTicketStatusRequest.builder()
                .status(TicketStatus.IN_PROGRESS)
                .notes("Work started")
                .build();

        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // =========================================================================
    // Test 5 — PUT /api/tickets/{id}/status — invalid transition NEW→DONE → 409
    // =========================================================================

    @Test
    @DisplayName("PUT /api/tickets/{id}/status — invalid transition NEW to DONE returns 409 INVALID_STATUS_TRANSITION")
    void updateStatus_invalidTransition_returns409() throws Exception {
        UUID blockId = createBlock("TBlock5-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T501");
        UUID ticketId = createTicket(adminToken, apartmentId, TicketCategory.COMPLAINT);

        UpdateTicketStatusRequest req = UpdateTicketStatusRequest.builder()
                .status(TicketStatus.DONE)
                .build();

        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // Test 6 — POST /api/tickets/{id}/rate on DONE ticket → 200
    // =========================================================================

    @Test
    @DisplayName("POST /api/tickets/{id}/rate — resident rates DONE ticket, returns 200")
    void rateTicket_doneTicked_returns200() throws Exception {
        UUID blockId = createBlock("TBlock6-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T601");
        String uid6 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phone6 = phoneFromUid(uid6);
        assignResident(phone6, apartmentId);
        String residentToken = login(phone6, "Password@123456");

        UUID ticketId = createTicket(residentToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        // ADMIN moves ticket through to DONE: NEW → ASSIGNED → IN_PROGRESS → DONE.
        String techUid6 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId = createUser(phoneFromUid(techUid6), UserRole.TECHNICIAN);

        AssignTicketRequest assignReq = AssignTicketRequest.builder()
                .assignedToUserId(techId)
                .build();
        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UpdateTicketStatusRequest.builder().status(TicketStatus.IN_PROGRESS).build())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UpdateTicketStatusRequest.builder().status(TicketStatus.DONE)
                                        .resolutionNotes("Fixed.").build())))
                .andExpect(status().isOk());

        // Resident rates the DONE ticket.
        RateTicketRequest rateReq = RateTicketRequest.builder()
                .rating(5)
                .comment("Excellent service")
                .build();

        mockMvc.perform(post("/api/tickets/" + ticketId + "/rate")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));
    }

    // =========================================================================
    // Test 7 — POST /api/tickets/{id}/rate on IN_PROGRESS ticket → 409
    // =========================================================================

    @Test
    @DisplayName("POST /api/tickets/{id}/rate — ticket not DONE returns 409 CONFLICT")
    void rateTicket_notDone_returns409() throws Exception {
        UUID blockId = createBlock("TBlock7-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "T701");
        String uid7 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phone7 = phoneFromUid(uid7);
        assignResident(phone7, apartmentId);
        String residentToken = login(phone7, "Password@123456");

        UUID ticketId = createTicket(residentToken, apartmentId, TicketCategory.COMPLAINT);

        // Move to IN_PROGRESS but not DONE.
        String techUid7 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId = createUser(phoneFromUid(techUid7), UserRole.TECHNICIAN);

        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AssignTicketRequest.builder().assignedToUserId(techId).build())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UpdateTicketStatusRequest.builder().status(TicketStatus.IN_PROGRESS).build())))
                .andExpect(status().isOk());

        RateTicketRequest rateReq = RateTicketRequest.builder().rating(4).build();

        mockMvc.perform(post("/api/tickets/" + ticketId + "/rate")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // Test 8 — GET /api/tickets as RESIDENT only returns own apartment's tickets
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets — RESIDENT only sees tickets for their own apartment")
    void listTickets_resident_onlyOwnApartment() throws Exception {
        UUID blockId = createBlock("TBlock8-" + System.nanoTime());
        UUID aptA = createApartment(blockId, "TA801");
        UUID aptB = createApartment(blockId, "TA802");

        String uidA = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phoneA = phoneFromUid(uidA);
        assignResident(phoneA, aptA);
        String tokenA = login(phoneA, "Password@123456");

        String uidB = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phoneB = phoneFromUid(uidB);
        assignResident(phoneB, aptB);

        // Create one ticket for apartment A and one for apartment B (as admin).
        UUID ticketA = createTicket(tokenA, aptA, TicketCategory.COMPLAINT);
        createTicket(adminToken, aptB, TicketCategory.ADMINISTRATIVE);

        // Resident A's list should only contain ticketA.
        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        java.util.List<?> data = (java.util.List<?>) body.get("data");

        // All returned tickets must belong to apartment A.
        for (Object item : data) {
            Map<?, ?> ticket = (Map<?, ?>) item;
            Map<?, ?> apartment = (Map<?, ?>) ticket.get("apartment");
            org.junit.jupiter.api.Assertions.assertEquals(aptA.toString(), apartment.get("id"));
        }

        // ticketA must be present.
        boolean found = data.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(t -> ticketA.toString().equals(t.get("id")));
        org.junit.jupiter.api.Assertions.assertTrue(found, "ticketA should appear in resident A's list");
    }

    // =========================================================================
    // Test 9 — GET /api/tickets?status=NEW&status=ASSIGNED — multi-status filter
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets — multi-status ?status=NEW&status=ASSIGNED returns 200, only matching statuses")
    void listTickets_multiStatusFilter_returns200OnlyMatchingStatuses() throws Exception {
        UUID blockId = createBlock("TBlock9-" + System.nanoTime());
        UUID aptId = createApartment(blockId, "T901");

        UUID ticketNew = createTicket(adminToken, aptId, TicketCategory.COMPLAINT);
        UUID ticketAssigned = createTicket(adminToken, aptId, TicketCategory.MAINTENANCE_REPAIR);

        String techUid9 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId = createUser(phoneFromUid(techUid9), UserRole.TECHNICIAN);

        // Move ticketAssigned → ASSIGNED via assign endpoint.
        mockMvc.perform(put("/api/tickets/" + ticketAssigned + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AssignTicketRequest.builder().assignedToUserId(techId).build())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "NEW", "ASSIGNED")
                        .param("apartmentId", aptId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data = (List<?>) body.get("data");

        Set<String> allowed = Set.of("NEW", "ASSIGNED");
        for (Object item : data) {
            String st = (String) ((Map<?, ?>) item).get("status");
            org.junit.jupiter.api.Assertions.assertTrue(allowed.contains(st),
                    "Expected status in [NEW, ASSIGNED] but got: " + st);
        }

        Set<String> ids = data.stream()
                .map(i -> (String) ((Map<?, ?>) i).get("id"))
                .collect(Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertTrue(ids.contains(ticketNew.toString()),
                "NEW ticket must appear");
        org.junit.jupiter.api.Assertions.assertTrue(ids.contains(ticketAssigned.toString()),
                "ASSIGNED ticket must appear");
    }

    // =========================================================================
    // Test 10 — GET /api/tickets (no status) returns all role-scoped tickets
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets — no status filter returns 200 with all scoped tickets")
    void listTickets_noStatusFilter_returns200All() throws Exception {
        UUID blockId = createBlock("TBlock10-" + System.nanoTime());
        UUID aptId = createApartment(blockId, "T1001");

        UUID ticket1 = createTicket(adminToken, aptId, TicketCategory.COMPLAINT);
        UUID ticket2 = createTicket(adminToken, aptId, TicketCategory.ADMINISTRATIVE);

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", aptId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Set<String> ids = ((List<?>) body.get("data")).stream()
                .map(i -> (String) ((Map<?, ?>) i).get("id"))
                .collect(Collectors.toSet());

        org.junit.jupiter.api.Assertions.assertTrue(ids.contains(ticket1.toString()));
        org.junit.jupiter.api.Assertions.assertTrue(ids.contains(ticket2.toString()));
    }

    // =========================================================================
    // Test 11 — GET /api/tickets?status=NEW single value — backward compat
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets — single ?status=NEW still returns 200 (backward compatibility)")
    void listTickets_singleStatus_backwardCompat() throws Exception {
        UUID blockId = createBlock("TBlock11-" + System.nanoTime());
        UUID aptId = createApartment(blockId, "T1101");

        UUID newTicket = createTicket(adminToken, aptId, TicketCategory.COMPLAINT);

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "NEW")
                        .param("apartmentId", aptId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data = (List<?>) body.get("data");

        for (Object item : data) {
            org.junit.jupiter.api.Assertions.assertEquals("NEW",
                    ((Map<?, ?>) item).get("status"), "All returned items must have status=NEW");
        }

        Set<String> ids = data.stream()
                .map(i -> (String) ((Map<?, ?>) i).get("id"))
                .collect(Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertTrue(ids.contains(newTicket.toString()));
    }

    // =========================================================================
    // Test 12 — GET /api/tickets?status=BOGUS → 400 VALIDATION_ERROR
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets — invalid ?status=BOGUS returns 400 VALIDATION_ERROR")
    void listTickets_invalidStatus_returns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // Test 13 — G3: rogue isPublic field on status update is ignored (N3 P5)
    // =========================================================================

    @Test
    @DisplayName("PUT /api/tickets/{id}/status — rogue isPublic field in body is ignored, flag stays false")
    void updateStatus_rogueIsPublicField_isIgnored() throws Exception {
        UUID blockId = createBlock("TBlock13-" + System.nanoTime());
        UUID aptId = createApartment(blockId, "T13-" + System.nanoTime());
        UUID ticketId = createTicket(adminToken, aptId, TicketCategory.COMPLAINT);

        // No DTO field maps isPublic on any update path (G3) — the JSON key must be ignored.
        Map<String, Object> body = new HashMap<>();
        body.put("status", "CANCELLED");
        body.put("isPublic", true);
        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(false));

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(false));
    }
}
