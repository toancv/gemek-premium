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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.ticket.dto.AssignTicketRequest;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G4 cross-module integration tests for notifications.
 *
 * <p>Covers:
 * <ol>
 *   <li>Assigning a ticket to a technician creates a TICKET_ASSIGNED notification for that technician.</li>
 *   <li>Injecting 3 unread notifications, marking all read → unread-count drops to 0.</li>
 *   <li>Injecting 2 unread notifications, marking one read by ID → unread-count = 1.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private String adminToken;
    private UUID adminUserId;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_EMAIL    = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
        adminUserId = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Seeded admin user not found"))
                .getId();
    }

    // =========================================================================
    // Test 1 — ticket assignment creates TICKET_ASSIGNED notification for technician
    // =========================================================================

    @Test
    @DisplayName("Admin assigns ticket to technician → technician receives TICKET_ASSIGNED notification")
    void ticketAssignment_createsNotification() throws Exception {
        // Create apartment hierarchy.
        UUID blockId     = createBlock("NI1-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "NI101");

        // Create ticket as admin.
        UUID ticketId = createTicket(adminToken, apartmentId, TicketCategory.MAINTENANCE_REPAIR);

        // Create a technician user and obtain their token.
        String techUid   = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String techPhone = phoneFromUid(techUid);
        UUID techId      = createUser(techPhone, UserRole.TECHNICIAN);
        String techToken = login(techPhone, "Password@123456");

        // Capture technician's unread count before assignment.
        long unreadBefore = notificationRepository.countUnreadByUserId(techId);

        // Admin assigns the ticket.
        AssignTicketRequest req = AssignTicketRequest.builder()
                .assignedToUserId(techId)
                .scheduledDate(LocalDate.now().plusDays(1))
                .build();
        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Technician's unread count must have increased by at least 1.
        long unreadAfter = notificationRepository.countUnreadByUserId(techId);
        assertThat(unreadAfter).isGreaterThan(unreadBefore);

        // Technician's notification list must contain a TICKET_ASSIGNED entry.
        MvcResult listResult = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> listBody = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        List<?> data       = (List<?>) listBody.get("data");
        boolean hasAssigned = data.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(n -> "TICKET_ASSIGNED".equals(n.get("type")));
        assertTrue(hasAssigned, "Technician must have at least one TICKET_ASSIGNED notification after assignment");
    }

    // =========================================================================
    // Test 2 — inject 3 notifications, mark all read → unread-count = 0
    // =========================================================================

    @Test
    @DisplayName("3 unread notifications → POST /read-all → unread-count = 0")
    void markAllRead_decrementsUnreadCount() throws Exception {
        // Create a dedicated user so other tests' notifications don't interfere.
        String userUid   = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String userPhone = phoneFromUid(userUid);
        UUID userId      = createUser(userPhone, UserRole.TECHNICIAN);
        String userToken = login(userPhone, "Password@123456");

        // Inject exactly 3 unread notifications via service.
        notificationService.createNotification(userId, "Notif A", null, NotificationType.GENERAL, null, null);
        notificationService.createNotification(userId, "Notif B", null, NotificationType.GENERAL, null, null);
        notificationService.createNotification(userId, "Notif C", null, NotificationType.GENERAL, null, null);

        // Unread count must be at least 3 (may have prior unread if user already had any).
        MvcResult countBefore = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber())
                .andReturn();
        int unreadBefore = ((Number) ((Map<?, ?>) objectMapper.readValue(
                countBefore.getResponse().getContentAsString(), Map.class)).get("unreadCount")).intValue();
        assertThat(unreadBefore).isGreaterThanOrEqualTo(3);

        // Mark all as read.
        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Unread count must be 0 now.
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // =========================================================================
    // Test 3 — inject 2 notifications, mark one read → unread-count = 1
    // =========================================================================

    @Test
    @DisplayName("2 unread notifications → mark one by ID → unread-count = 1, that notification isRead=true")
    void markSingleRead() throws Exception {
        // Create isolated user.
        String userUid2  = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String userPhone2 = phoneFromUid(userUid2);
        UUID userId      = createUser(userPhone2, UserRole.TECHNICIAN);
        String userToken = login(userPhone2, "Password@123456");

        // Inject 2 unread notifications.
        String titleA = "SingleA-" + System.nanoTime();
        String titleB = "SingleB-" + System.nanoTime();
        notificationService.createNotification(userId, titleA, null, NotificationType.GENERAL, null, null);
        notificationService.createNotification(userId, titleB, null, NotificationType.GENERAL, null, null);

        // Both must appear as unread.
        long unreadCount = notificationRepository.countUnreadByUserId(userId);
        assertEquals(2, unreadCount, "Exactly 2 unread notifications expected after injection");

        // Retrieve notification IDs from the list endpoint.
        MvcResult listResult = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> listBody = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        List<?> data       = (List<?>) listBody.get("data");

        UUID notifAId = data.stream()
                .map(i -> (Map<?, ?>) i)
                .filter(n -> titleA.equals(n.get("title")))
                .map(n -> UUID.fromString((String) n.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Notification A not found in list"));

        // Mark notification A as read.
        mockMvc.perform(post("/api/notifications/" + notifAId + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Unread count must now be 1.
        long unreadAfter = notificationRepository.countUnreadByUserId(userId);
        assertEquals(1, unreadAfter, "Unread count must be 1 after marking one of two notifications read");

        // Notification A must be marked isRead=true in the list.
        MvcResult listAfter = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        List<?> dataAfter = (List<?>) ((Map<?, ?>) objectMapper.readValue(
                listAfter.getResponse().getContentAsString(), Map.class)).get("data");

        boolean notifAIsRead = dataAfter.stream()
                .map(i -> (Map<?, ?>) i)
                .filter(n -> notifAId.toString().equals(n.get("id")))
                .map(n -> Boolean.TRUE.equals(n.get("isRead")) || Boolean.TRUE.equals(n.get("read")))
                .findFirst()
                .orElse(false);
        assertTrue(notifAIsRead, "Notification A must have isRead=true after marking as read");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
}
