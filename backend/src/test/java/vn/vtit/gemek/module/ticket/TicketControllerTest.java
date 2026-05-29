/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;

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
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;
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
import java.util.Map;
import java.util.UUID;

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
@Testcontainers
@ActiveProfiles("test")
class TicketControllerTest {

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

    private String adminToken;
    private String technicianToken;

    private static final String ADMIN_EMAIL    = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @BeforeEach
    void setUp() throws Exception {
        // Presign mock returns a fixed URL for any key.
        when(fileStorageService.presign(anyString())).thenReturn("http://minio/presigned-url");

        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Logs in and returns the access token.
     *
     * @param email    user email.
     * @param password user password.
     * @return the JWT access token string.
     */
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
     * @param email the user email.
     * @param role  the user role.
     * @return the created user UUID.
     */
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

    /**
     * Assigns a user as an active resident of the given apartment.
     *
     * @param userId      the user UUID.
     * @param apartmentId the apartment UUID.
     */
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
        String email = "res.tick1." + System.nanoTime() + "@test.com";
        UUID userId = createUser(email, UserRole.RESIDENT);
        assignResident(userId, apartmentId);
        String residentToken = login(email, "Password@123456");

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

        String techEmail = "tech.assign." + System.nanoTime() + "@test.com";
        UUID techId = createUser(techEmail, UserRole.TECHNICIAN);

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

        String techEmail = "tech.status." + System.nanoTime() + "@test.com";
        UUID techId = createUser(techEmail, UserRole.TECHNICIAN);

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
        String email = "res.rate." + System.nanoTime() + "@test.com";
        UUID userId = createUser(email, UserRole.RESIDENT);
        assignResident(userId, apartmentId);
        String residentToken = login(email, "Password@123456");

        UUID ticketId = createTicket(residentToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        // ADMIN moves ticket through to DONE: NEW → ASSIGNED → IN_PROGRESS → DONE.
        String techEmail = "tech.rate." + System.nanoTime() + "@test.com";
        UUID techId = createUser(techEmail, UserRole.TECHNICIAN);

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
        String email = "res.rate2." + System.nanoTime() + "@test.com";
        UUID userId = createUser(email, UserRole.RESIDENT);
        assignResident(userId, apartmentId);
        String residentToken = login(email, "Password@123456");

        UUID ticketId = createTicket(residentToken, apartmentId, TicketCategory.COMPLAINT);

        // Move to IN_PROGRESS but not DONE.
        String techEmail = "tech.rate2." + System.nanoTime() + "@test.com";
        UUID techId = createUser(techEmail, UserRole.TECHNICIAN);

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
                .andExpect(jsonPath("$.error").value("CONFLICT"));
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

        String emailA = "res.scopeA." + System.nanoTime() + "@test.com";
        UUID userA = createUser(emailA, UserRole.RESIDENT);
        assignResident(userA, aptA);
        String tokenA = login(emailA, "Password@123456");

        String emailB = "res.scopeB." + System.nanoTime() + "@test.com";
        UUID userB = createUser(emailB, UserRole.RESIDENT);
        assignResident(userB, aptB);

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
}
