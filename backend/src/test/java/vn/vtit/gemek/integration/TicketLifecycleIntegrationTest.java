/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractorRequest;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.ticket.dto.AssignTicketRequest;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.UpdateTicketStatusRequest;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G4 cross-module integration tests for the full ticket lifecycle.
 *
 * <p>Covers:
 * <ol>
 *   <li>Resident-created ticket through NEW → ASSIGNED → IN_PROGRESS → DONE → rated (no contractor).</li>
 *   <li>MAINTENANCE_REPAIR ticket assigned to contractor, rated, contractor rating recalculated.</li>
 *   <li>SLA breach detection visible in dashboard overdueRequests field.</li>
 *   <li>Contractor assignment rejected on non-MAINTENANCE_REPAIR ticket.</li>
 *   <li>Resident list scoping — resident only sees their own apartment's tickets.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TicketLifecycleIntegrationTest {

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
        registry.add("minio.endpoint", () -> "http://localhost:9000");
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket", () -> "gemek-test");
    }

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ContractorRepository contractorRepository;

    private String adminToken;

    private static final String ADMIN_EMAIL    = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @BeforeEach
    void setUp() throws Exception {
        when(fileStorageService.presign(anyString())).thenReturn("http://minio/presigned-url");
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    // =========================================================================
    // Test 1 — full lifecycle without contractor; rating persisted, no contractor touched
    // =========================================================================

    @Test
    @DisplayName("Full lifecycle: resident creates → admin assigns technician → IN_PROGRESS → DONE → resident rates; status=DONE, rating persisted")
    void createAndAssignTicket_fullLifecycle() throws Exception {
        UUID blockId     = createBlock("LC1-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "LC101");
        String email     = "res.lc1." + System.nanoTime() + "@test.com";
        UUID userId      = createUser(email, UserRole.RESIDENT);
        assignResident(userId, apartmentId);
        String residentToken = login(email, "Password@123456");

        // Resident creates ticket.
        UUID ticketId = createTicket(residentToken, apartmentId, TicketCategory.COMPLAINT);

        // Admin assigns to technician.
        String techEmail = "tech.lc1." + System.nanoTime() + "@test.com";
        UUID techId = createUser(techEmail, UserRole.TECHNICIAN);
        assignToUser(ticketId, techId);

        // Move to IN_PROGRESS.
        updateStatus(ticketId, TicketStatus.IN_PROGRESS, null);

        // Move to DONE.
        updateStatus(ticketId, TicketStatus.DONE, "Issue resolved.");

        // Resident rates.
        RateTicketRequest rateReq = RateTicketRequest.builder().rating(4).comment("Good work").build();
        MvcResult rateResult = mockMvc.perform(post("/api/tickets/" + ticketId + "/rate")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateReq)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> rated = objectMapper.readValue(rateResult.getResponse().getContentAsString(), Map.class);
        assertEquals("DONE", rated.get("status"), "Ticket status must be DONE after rating");
        assertEquals(4, rated.get("rating"), "Rating must be persisted as 4");
        // No contractor was involved — contractor field must be null.
        assertNull(rated.get("assignedToContractor"), "No contractor should be assigned on a COMPLAINT ticket");
    }

    // =========================================================================
    // Test 2 — MAINTENANCE_REPAIR via contractor; rating triggers contractor recalculation
    // =========================================================================

    @Test
    @DisplayName("MAINTENANCE_REPAIR assigned to contractor → DONE → resident rates → contractor.rating recalculated")
    void maintenanceTicket_assignedToContractor_fullLifecycle() throws Exception {
        UUID blockId     = createBlock("LC2-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "LC201");
        String email     = "res.lc2." + System.nanoTime() + "@test.com";
        UUID userId      = createUser(email, UserRole.RESIDENT);
        assignResident(userId, apartmentId);
        String residentToken = login(email, "Password@123456");

        // Create contractor.
        UUID contractorId = createContractor("FixIt Corp " + System.nanoTime());

        // Admin creates MAINTENANCE_REPAIR ticket.
        UUID ticketId = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        // Admin assigns to contractor.
        AssignTicketRequest assignReq = AssignTicketRequest.builder()
                .assignedToContractorId(contractorId)
                .scheduledDate(LocalDate.now().plusDays(2))
                .build();
        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // Move to IN_PROGRESS.
        updateStatus(ticketId, TicketStatus.IN_PROGRESS, null);

        // Move to DONE.
        updateStatus(ticketId, TicketStatus.DONE, "Contractor finished work.");

        // Resident rates with 5 stars.
        RateTicketRequest rateReq = RateTicketRequest.builder().rating(5).comment("Excellent").build();
        mockMvc.perform(post("/api/tickets/" + ticketId + "/rate")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));

        // Contractor rating must now be non-null and equal to 5.00 (sole rated ticket).
        BigDecimal contractorRating = contractorRepository.findById(contractorId)
                .orElseThrow(() -> new AssertionError("Contractor not found: " + contractorId))
                .getRating();
        assertNotNull(contractorRating, "Contractor rating must be set after ticket is rated");
        assertEquals(0, BigDecimal.valueOf(5).compareTo(contractorRating.setScale(0, java.math.RoundingMode.HALF_UP)),
                "Contractor average rating must equal 5 (sole rated ticket)");
    }

    // =========================================================================
    // Test 3 — SLA breach: backdated slaDeadline → dashboard shows overdueRequests > 0
    // =========================================================================

    @Test
    @DisplayName("SLA breach: ticket with past slaDeadline and non-DONE status appears in dashboard overdueRequests")
    void slaBreachDetected() throws Exception {
        UUID blockId     = createBlock("LC3-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "LC301");

        // Create ticket via API (status=NEW).
        UUID ticketId = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        // Backdated the SLA deadline directly via repository so the ticket is already overdue.
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            ticket.setSlaDeadline(OffsetDateTime.now().minusHours(48));
            ticketRepository.save(ticket);
        });

        // Dashboard must report at least 1 overdue request.
        MvcResult dashboard = mockMvc.perform(get("/api/reports/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body    = objectMapper.readValue(dashboard.getResponse().getContentAsString(), Map.class);
        Map<?, ?> tickets = (Map<?, ?>) body.get("tickets");
        assertNotNull(tickets, "Dashboard must contain 'tickets' section");
        Number overdueRequests = (Number) tickets.get("overdueRequests");
        assertNotNull(overdueRequests, "Dashboard tickets.overdueRequests must be present");
        assertTrue(overdueRequests.intValue() > 0,
                "overdueRequests must be > 0 after inserting a breached ticket; was: " + overdueRequests);
    }

    // =========================================================================
    // Test 4 — Contractor assignment rejected on COMPLAINT ticket
    // =========================================================================

    @Test
    @DisplayName("Assigning contractor to COMPLAINT ticket returns 400 CONTRACTOR_ASSIGNMENT_NOT_ALLOWED")
    void nonMaintenanceTicket_contractorAssignment_rejected() throws Exception {
        UUID blockId     = createBlock("LC4-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "LC401");
        UUID ticketId    = createTicket(adminToken, apartmentId, TicketCategory.COMPLAINT);

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
    // Test 5 — Resident list scoping: resident only sees own apartment's tickets
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets as RESIDENT returns only tickets for their own apartment")
    void residentCanOnlySeeOwnTickets() throws Exception {
        UUID blockId = createBlock("LC5-" + System.nanoTime());
        UUID aptA    = createApartment(blockId, "LC5A");
        UUID aptB    = createApartment(blockId, "LC5B");

        String emailA = "res.lc5a." + System.nanoTime() + "@test.com";
        UUID userA    = createUser(emailA, UserRole.RESIDENT);
        assignResident(userA, aptA);
        String tokenA = login(emailA, "Password@123456");

        String emailB = "res.lc5b." + System.nanoTime() + "@test.com";
        UUID userB    = createUser(emailB, UserRole.RESIDENT);
        assignResident(userB, aptB);

        // Create one ticket per apartment.
        UUID ticketA = createTicket(tokenA, aptA, TicketCategory.COMPLAINT);
        createTicket(adminToken, aptB, TicketCategory.ADMINISTRATIVE);

        // Resident A's list.
        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data   = (List<?>) body.get("data");

        // Every ticket in the list must belong to apartment A.
        for (Object item : data) {
            Map<?, ?> ticket    = (Map<?, ?>) item;
            Map<?, ?> apartment = (Map<?, ?>) ticket.get("apartment");
            assertEquals(aptA.toString(), apartment.get("id"),
                    "Resident A must only see tickets for their own apartment");
        }

        // Ticket A must be present.
        boolean found = data.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(t -> ticketA.toString().equals(t.get("id")));
        assertTrue(found, "ticketA must appear in resident A's list");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
    }

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

    private UUID createUser(String email, UserRole role) throws Exception {
        CreateUserRequest req = new CreateUserRequest(
                email, "Test User", "0900000001", role, "Password@123456");
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private void assignResident(UUID userId, UUID apartmentId) throws Exception {
        CreateResidentRequest req = new CreateResidentRequest(
                userId, apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), true, null);
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private UUID createTicket(String token, UUID apartmentId, TicketCategory category) throws Exception {
        CreateTicketRequest req = CreateTicketRequest.builder()
                .apartmentId(apartmentId)
                .category(category)
                .title("Ticket " + System.nanoTime())
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

    private void assignToUser(UUID ticketId, UUID userId) throws Exception {
        AssignTicketRequest req = AssignTicketRequest.builder()
                .assignedToUserId(userId)
                .scheduledDate(LocalDate.now().plusDays(1))
                .build();
        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private void updateStatus(UUID ticketId, TicketStatus status, String resolutionNotes) throws Exception {
        UpdateTicketStatusRequest req = UpdateTicketStatusRequest.builder()
                .status(status)
                .resolutionNotes(resolutionNotes)
                .build();
        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private UUID createContractor(String companyName) throws Exception {
        CreateContractorRequest req = new CreateContractorRequest(
                companyName, "Contact Person", "0900000099",
                "contractor@test.com", null, ContractorSpecialty.PLUMBING, null, null);
        MvcResult result = mockMvc.perform(post("/api/contractors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }
}
