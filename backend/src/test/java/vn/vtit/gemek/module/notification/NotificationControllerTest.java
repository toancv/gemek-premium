/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;

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
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link NotificationController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Covers:
 * <ol>
 *   <li>GET /api/notifications — 200, list contains the seeded notification.</li>
 *   <li>POST /api/notifications/{id}/read — 200, subsequent unread count is decremented.</li>
 *   <li>GET /api/notifications/unread-count — 200, {@code unreadCount} field is present.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

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

        // Resolve the admin user's UUID for direct service calls.
        adminUserId = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Seeded admin user not found"))
                .getId();
    }

    // =========================================================================
    // Test 1 — list notifications contains the seeded entry
    // =========================================================================

    @Test
    @DisplayName("GET /api/notifications — 200, seeded notification appears in list")
    void getMyNotifications_withSeededNotification_returnsItInList() throws Exception {
        // Create a notification directly via the service so we have a known record.
        notificationService.createNotification(
                adminUserId,
                "Test Notification Title",
                "Test body text.",
                NotificationType.GENERAL,
                null,
                null
        );

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                // The seeded notification title must appear somewhere in the first page.
                .andExpect(jsonPath("$.data[?(@.title == 'Test Notification Title')]").exists());
    }

    // =========================================================================
    // Test 2 — mark single notification as read decrements unread count
    // =========================================================================

    @Test
    @DisplayName("POST /api/notifications/{id}/read — 200, unread count decrements")
    void markAsRead_unreadNotification_decrementsUnreadCount() throws Exception {
        // Create an unread notification via service.
        notificationService.createNotification(
                adminUserId,
                "Unread Notification",
                "Will be marked as read.",
                NotificationType.GENERAL,
                null,
                null
        );

        // Capture the unread count before marking.
        long countBefore = notificationRepository.countUnreadByUserId(adminUserId);
        assertThat(countBefore).isGreaterThan(0);

        // Retrieve the notification ID from the list endpoint.
        MvcResult listResult = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> listBody = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        java.util.List<?> data = (java.util.List<?>) listBody.get("data");
        // Find the notification we just created by title.
        UUID notificationId = data.stream()
                .map(item -> (Map<?, ?>) item)
                .filter(item -> "Unread Notification".equals(item.get("title")))
                .map(item -> UUID.fromString((String) item.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seeded notification not found in list"));

        // Mark it as read.
        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Unread count must have decreased by exactly 1.
        long countAfter = notificationRepository.countUnreadByUserId(adminUserId);
        assertThat(countAfter).isEqualTo(countBefore - 1);
    }

    // =========================================================================
    // Test 3 — unread-count endpoint returns the field
    // =========================================================================

    @Test
    @DisplayName("GET /api/notifications/unread-count — 200, unreadCount field present")
    void getUnreadCount_authenticated_returnsUnreadCountField() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber());
    }
}
