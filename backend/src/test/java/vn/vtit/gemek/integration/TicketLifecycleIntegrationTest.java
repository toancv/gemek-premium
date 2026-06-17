/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;

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
import vn.vtit.gemek.module.contractor.dto.CreateContractorRequest;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import java.util.HashMap;
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
import vn.vtit.gemek.module.user.repository.UserRepository;

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
@ActiveProfiles("test")
class TicketLifecycleIntegrationTest {

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

    @Autowired
    private UserRepository userRepository;

    private String adminToken;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    @BeforeEach
    void setUp() throws Exception {
        when(fileStorageService.presign(anyString())).thenReturn("http://minio/presigned-url");
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
    }

    // =========================================================================
    // Test 1 — full lifecycle without contractor; rating persisted, no contractor touched
    // =========================================================================

    @Test
    @DisplayName("Full lifecycle: resident creates → admin assigns technician → IN_PROGRESS → DONE → resident rates; status=DONE, rating persisted")
    void createAndAssignTicket_fullLifecycle() throws Exception {
        String uid1      = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId     = createBlock("LC1-" + uid1);
        UUID apartmentId = createApartment(blockId, "LC101");
        String phone1    = phoneFromUid(uid1);
        assignResident(phone1, apartmentId);
        String residentToken = login(phone1, "Password@123456");

        // Resident creates ticket.
        UUID ticketId = createTicket(residentToken, apartmentId, TicketCategory.COMPLAINT);

        // Admin assigns to technician.
        String techUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId = createUser(phoneFromUid(techUid), UserRole.TECHNICIAN);
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
        String uid2      = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId     = createBlock("LC2-" + uid2);
        UUID apartmentId = createApartment(blockId, "LC201");
        String phone2    = phoneFromUid(uid2);
        assignResident(phone2, apartmentId);
        String residentToken = login(phone2, "Password@123456");

        // Create contractor.
        UUID contractorId = createContractor("FixIt Corp " + System.nanoTime());

        // Resident submits MAINTENANCE_REPAIR ticket (so they can rate it on completion).
        UUID ticketId = createTicket(residentToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

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

        String uidA = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phoneA = phoneFromUid(uidA);
        assignResident(phoneA, aptA);
        String tokenA = login(phoneA, "Password@123456");

        String uidB = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        assignResident(phoneFromUid(uidB), aptB);

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
    // Test 6 — overdue filter (P2.6): GET /api/tickets?overdue=true returns only
    //          breached-open tickets, mirroring the canonical SLA-breach predicate
    //          (sla_deadline < now AND status NOT IN (DONE,CANCELLED)).
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets?overdue=true returns only tickets with past sla_deadline AND status NOT IN (DONE,CANCELLED); DONE, NULL-deadline and future-deadline tickets excluded")
    void overdueTrue_returnsOnlyBreachedOpenTickets() throws Exception {
        UUID blockId     = createBlock("OF1-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "OF101");

        // Four tickets in one apartment — apartmentId filter isolates the assertion
        // from shared-DB pollution. Deadlines/statuses set directly via repo (same
        // backdate pattern as Test 3) to build exact fixtures without the state machine.
        UUID tOverdue = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        UUID tDone    = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        UUID tNull    = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        UUID tFuture  = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        setDeadlineAndStatus(tOverdue, OffsetDateTime.now().minusHours(48), TicketStatus.NEW);
        // Past deadline but DONE → closed → must be EXCLUDED.
        setDeadlineAndStatus(tDone, OffsetDateTime.now().minusHours(48), TicketStatus.DONE);
        // NULL deadline → never overdue → must be EXCLUDED.
        setDeadlineAndStatus(tNull, null, TicketStatus.NEW);
        // Future deadline → not yet overdue → must be EXCLUDED.
        setDeadlineAndStatus(tFuture, OffsetDateTime.now().plusHours(48), TicketStatus.NEW);

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", apartmentId.toString())
                        .param("overdue", "true"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data   = (List<?>) body.get("data");
        List<String> ids = data.stream().map(i -> (String) ((Map<?, ?>) i).get("id")).toList();

        assertEquals(1, ids.size(), "Only the breached-open ticket must match overdue=true; was: " + ids);
        assertTrue(ids.contains(tOverdue.toString()), "Breached-open ticket must be present");
        assertTrue(!ids.contains(tDone.toString()), "DONE ticket past deadline must be excluded");
        assertTrue(!ids.contains(tNull.toString()), "NULL-deadline ticket must be excluded");
        assertTrue(!ids.contains(tFuture.toString()), "Future-deadline ticket must be excluded");
        // total (whole-dataset count) must agree with the single matched row.
        assertEquals(1, ((Number) body.get("total")).intValue(), "total must equal the matched-row count");
    }

    @Test
    @DisplayName("GET /api/tickets with overdue absent returns all tickets regardless of deadline/status (regression guard — unchanged behavior)")
    void overdueAbsent_returnsAll_unchangedBehavior() throws Exception {
        UUID blockId     = createBlock("OF2-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "OF201");

        UUID tOverdue = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        UUID tDone    = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        UUID tNull    = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        UUID tFuture  = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        setDeadlineAndStatus(tOverdue, OffsetDateTime.now().minusHours(48), TicketStatus.NEW);
        setDeadlineAndStatus(tDone, OffsetDateTime.now().minusHours(48), TicketStatus.DONE);
        setDeadlineAndStatus(tNull, null, TicketStatus.NEW);
        setDeadlineAndStatus(tFuture, OffsetDateTime.now().plusHours(48), TicketStatus.NEW);

        // No overdue param → existing behavior: all four returned.
        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", apartmentId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertEquals(4, ((Number) body.get("total")).intValue(),
                "overdue absent must not filter — all four tickets returned");
    }

    @Test
    @DisplayName("GET /api/tickets?overdue=true as TECHNICIAN keeps role-scope: sees own-assigned overdue ticket, not another technician's overdue ticket")
    void overdueTrue_respectsTechnicianRoleScope() throws Exception {
        UUID blockId     = createBlock("OF3-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "OF301");

        String tech1Uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID tech1Id    = createUser(phoneFromUid(tech1Uid), UserRole.TECHNICIAN);
        String tech1Token = login(phoneFromUid(tech1Uid), "Password@123456");

        String tech2Uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID tech2Id    = createUser(phoneFromUid(tech2Uid), UserRole.TECHNICIAN);

        // In-scope: assigned to tech1 (status → ASSIGNED), then backdated overdue.
        UUID inScope = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(inScope, tech1Id);
        // Out-of-scope for tech1: assigned to tech2 (ASSIGNED, not NEW), also overdue.
        UUID outScope = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(outScope, tech2Id);

        // Backdate deadlines only — preserve the ASSIGNED status set by assignment.
        ticketRepository.findById(inScope).ifPresent(t -> {
            t.setSlaDeadline(OffsetDateTime.now().minusHours(48));
            ticketRepository.save(t);
        });
        ticketRepository.findById(outScope).ifPresent(t -> {
            t.setSlaDeadline(OffsetDateTime.now().minusHours(48));
            ticketRepository.save(t);
        });

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + tech1Token)
                        .param("apartmentId", apartmentId.toString())
                        .param("overdue", "true"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data   = (List<?>) body.get("data");
        List<String> ids = data.stream().map(i -> (String) ((Map<?, ?>) i).get("id")).toList();

        assertTrue(ids.contains(inScope.toString()),
                "Technician must see their own assigned overdue ticket");
        assertTrue(!ids.contains(outScope.toString()),
                "Technician must NOT see another technician's overdue ticket — role-scope preserved under overdue=true");
    }

    // =========================================================================
    // Test 7 — mine filter (P2.8): GET /api/tickets?mine=true returns only tickets
    //          whose assignedToUser is the caller (server-derived from the principal,
    //          no client-supplied id). ANDed on top of role-scope. Unassigned tickets
    //          (assignedToUser NULL) and tickets assigned to another user are excluded.
    //          mine=false / absent = no assignee filtering (no-op).
    // =========================================================================

    @Test
    @DisplayName("GET /api/tickets?mine=true returns only tickets assigned to the caller; another user's and UNASSIGNED tickets excluded")
    void mineTrue_returnsOnlyTicketsAssignedToCaller() throws Exception {
        UUID adminId     = userRepository.findByPhone(ADMIN_PHONE).orElseThrow().getId();
        UUID blockId     = createBlock("MF1-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "MF101");

        String techUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId    = createUser(phoneFromUid(techUid), UserRole.TECHNICIAN);

        // Assigned to caller (the admin) → must be INCLUDED.
        UUID tMine = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tMine, adminId);
        // Assigned to another user → must be EXCLUDED.
        UUID tOther = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tOther, techId);
        // Never assigned (assignedToUser NULL) → must be EXCLUDED (null-safety).
        UUID tUnassigned = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", apartmentId.toString())
                        .param("mine", "true"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data   = (List<?>) body.get("data");
        List<String> ids = data.stream().map(i -> (String) ((Map<?, ?>) i).get("id")).toList();

        assertEquals(1, ids.size(), "Only the caller-assigned ticket must match mine=true; was: " + ids);
        assertTrue(ids.contains(tMine.toString()), "Caller-assigned ticket must be present");
        assertTrue(!ids.contains(tOther.toString()), "Ticket assigned to another user must be excluded");
        assertTrue(!ids.contains(tUnassigned.toString()), "UNASSIGNED (null assignee) ticket must be excluded");
        assertEquals(1, ((Number) body.get("total")).intValue(), "total must equal the matched-row count");
    }

    @Test
    @DisplayName("GET /api/tickets with mine absent returns all tickets regardless of assignee (regression guard — unchanged behavior)")
    void mineAbsent_returnsAll_unchangedBehavior() throws Exception {
        UUID adminId     = userRepository.findByPhone(ADMIN_PHONE).orElseThrow().getId();
        UUID blockId     = createBlock("MF2-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "MF201");

        String techUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId    = createUser(phoneFromUid(techUid), UserRole.TECHNICIAN);

        UUID tMine = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tMine, adminId);
        UUID tOther = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tOther, techId);
        createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR); // unassigned

        // No mine param → existing behavior: all three returned.
        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", apartmentId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertEquals(3, ((Number) body.get("total")).intValue(),
                "mine absent must not filter — all three tickets returned");
    }

    @Test
    @DisplayName("GET /api/tickets?mine=false is a no-op (chosen semantics): no assignee filtering — same as absent")
    void mineFalse_isNoOp_returnsAll() throws Exception {
        UUID adminId     = userRepository.findByPhone(ADMIN_PHONE).orElseThrow().getId();
        UUID blockId     = createBlock("MF3-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "MF301");

        String techUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId    = createUser(phoneFromUid(techUid), UserRole.TECHNICIAN);

        UUID tMine = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tMine, adminId);
        UUID tOther = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tOther, techId);
        createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR); // unassigned

        // mine=false → no assignee filtering (no-op), identical to absent: all three returned.
        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", apartmentId.toString())
                        .param("mine", "false"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertEquals(3, ((Number) body.get("total")).intValue(),
                "mine=false must be a no-op — all three tickets returned (same as absent)");
    }

    @Test
    @DisplayName("GET /api/tickets?mine=true as TECHNICIAN collapses scope to assigned-to-me: excludes NEW (in base scope) and another tech's ticket")
    void mineTrue_respectsTechnicianRoleScope() throws Exception {
        UUID blockId     = createBlock("MF4-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "MF401");

        String tech1Uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID tech1Id    = createUser(phoneFromUid(tech1Uid), UserRole.TECHNICIAN);
        String tech1Token = login(phoneFromUid(tech1Uid), "Password@123456");

        String tech2Uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID tech2Id    = createUser(phoneFromUid(tech2Uid), UserRole.TECHNICIAN);

        // NEW + unassigned: in tech1's BASE scope (status=NEW arm) but NOT assigned to tech1
        // → must drop out under mine=true (proves the NEW arm collapses under the AND).
        UUID tNew = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        // Assigned to tech1 → must be INCLUDED.
        UUID tMine = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tMine, tech1Id);
        // Assigned to tech2 → out of tech1 base scope and not mine → EXCLUDED.
        UUID tOther = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tOther, tech2Id);

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + tech1Token)
                        .param("apartmentId", apartmentId.toString())
                        .param("mine", "true"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data   = (List<?>) body.get("data");
        List<String> ids = data.stream().map(i -> (String) ((Map<?, ?>) i).get("id")).toList();

        assertTrue(ids.contains(tMine.toString()), "Technician must see their own assigned ticket under mine=true");
        assertTrue(!ids.contains(tNew.toString()),
                "NEW unassigned ticket (in base scope) must drop out under mine=true — assigned-to-me subset");
        assertTrue(!ids.contains(tOther.toString()),
                "Another technician's ticket must NOT appear — role-scope preserved under mine=true");
    }

    @Test
    @DisplayName("GET /api/tickets?mine=true&overdue=true returns the caller's own overdue-open tickets (both predicates AND)")
    void mineTrueOverdueTrue_returnsCallerOwnOverdueOpen() throws Exception {
        UUID adminId     = userRepository.findByPhone(ADMIN_PHONE).orElseThrow().getId();
        UUID blockId     = createBlock("MF5-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "MF501");

        String techUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID techId    = createUser(phoneFromUid(techUid), UserRole.TECHNICIAN);

        // Mine + overdue (past deadline, open) → INCLUDED.
        UUID tMineOverdue = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tMineOverdue, adminId);
        setDeadlineAndStatus(tMineOverdue, OffsetDateTime.now().minusHours(48), TicketStatus.ASSIGNED);
        // Mine but NOT overdue (future deadline) → EXCLUDED by overdue.
        UUID tMineFuture = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tMineFuture, adminId);
        setDeadlineAndStatus(tMineFuture, OffsetDateTime.now().plusHours(48), TicketStatus.ASSIGNED);
        // Overdue but NOT mine (assigned to another) → EXCLUDED by mine.
        UUID tOtherOverdue = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);
        assignToUser(tOtherOverdue, techId);
        setDeadlineAndStatus(tOtherOverdue, OffsetDateTime.now().minusHours(48), TicketStatus.ASSIGNED);

        MvcResult result = mockMvc.perform(get("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("apartmentId", apartmentId.toString())
                        .param("mine", "true")
                        .param("overdue", "true"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data   = (List<?>) body.get("data");
        List<String> ids = data.stream().map(i -> (String) ((Map<?, ?>) i).get("id")).toList();

        assertEquals(1, ids.size(), "Only the caller's own overdue-open ticket must match; was: " + ids);
        assertTrue(ids.contains(tMineOverdue.toString()), "Caller's own overdue-open ticket must be present");
        assertTrue(!ids.contains(tMineFuture.toString()), "Caller's non-overdue ticket must be excluded by overdue=true");
        assertTrue(!ids.contains(tOtherOverdue.toString()), "Another user's overdue ticket must be excluded by mine=true");
        assertEquals(1, ((Number) body.get("total")).intValue(), "total must equal the matched-row count");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Sets a ticket's SLA deadline and status directly via the repository, bypassing
     * the status state machine to build exact overdue/closed fixtures (same backdate
     * approach as Test 3). A {@code null} deadline clears the SLA deadline.
     */
    private void setDeadlineAndStatus(UUID ticketId, OffsetDateTime deadline, TicketStatus status) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            ticket.setSlaDeadline(deadline);
            ticket.setStatus(status);
            ticketRepository.save(ticket);
        });
    }

    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

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
