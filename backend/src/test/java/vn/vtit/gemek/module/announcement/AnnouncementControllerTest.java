/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

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
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import java.util.HashMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AnnouncementController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>{@link FileStorageService} is mocked because MinIO is not started in this test suite.
 * The 4 tests cover: draft creation, publish flow, resident mark-read, and resident-scoped list.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnouncementControllerTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

    /**
     * Authenticates and returns the JWT access token.
     *
     * @param phone    user phone.
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
     * Creates an apartment on floor 3 and returns its UUID.
     *
     * @param blockId    the parent block UUID.
     * @param unitNumber the unit number string.
     * @return the created apartment UUID.
     */
    private UUID createApartment(UUID blockId, String unitNumber) throws Exception {
        CreateApartmentRequest req = new CreateApartmentRequest(blockId, (short) 3, unitNumber, null, null);
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
     * Creates a new user+resident atomically via the new transactional endpoint.
     *
     * @param phone       the new user's phone (also used for login).
     * @param apartmentId the apartment UUID to assign the resident to.
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
     * Creates an ALL-scope GENERAL announcement draft and returns its UUID.
     *
     * @param token the caller's JWT token.
     * @param title the announcement title.
     * @return the created announcement UUID.
     */
    private UUID createAnnouncement(String token, String title) throws Exception {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest();
        req.setTitle(title);
        req.setContent("Content for: " + title);
        req.setType(AnnouncementType.GENERAL);
        req.setTargetScope(AnnouncementScope.ALL);

        MvcResult result = mockMvc.perform(post("/api/announcements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    // =========================================================================
    // Test 1 — POST /api/announcements → 201, publishedAt=null (draft)
    // =========================================================================

    @Test
    @DisplayName("POST /api/announcements — ADMIN creates draft, returns 201 with publishedAt null")
    void createAnnouncement_adminCreatesDraft_returns201PublishedAtNull() throws Exception {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest();
        req.setTitle("Water shutdown notice " + System.nanoTime());
        req.setContent("Water will be shut off from 08:00 to 12:00 on Monday.");
        req.setType(AnnouncementType.MAINTENANCE);
        req.setTargetScope(AnnouncementScope.ALL);

        MvcResult result = mockMvc.perform(post("/api/announcements")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("MAINTENANCE"))
                .andExpect(jsonPath("$.targetScope").value("ALL"))
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        // publishedAt must be null for a draft.
        assertNull(body.get("publishedAt"), "publishedAt should be null for a draft announcement");
    }

    // =========================================================================
    // Test 2 — POST /api/announcements/{id}/publish → 200, publishedAt set
    // =========================================================================

    @Test
    @DisplayName("POST /api/announcements/{id}/publish — ADMIN publishes draft, returns 200 with publishedAt set")
    void publishAnnouncement_adminPublishesDraft_returns200WithPublishedAt() throws Exception {
        UUID announcementId = createAnnouncement(adminToken, "Gym closure " + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/api/announcements/" + announcementId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(announcementId.toString()))
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        // publishedAt must be non-null after publish.
        assertNotNull(body.get("publishedAt"), "publishedAt should be set after publishing");
    }

    // =========================================================================
    // Test 3 — POST /api/announcements/{id}/read (RESIDENT) → 200
    // =========================================================================

    @Test
    @DisplayName("POST /api/announcements/{id}/read — RESIDENT marks announcement as read, returns 200 alreadyRead=false")
    void markRead_resident_returns200AlreadyReadFalse() throws Exception {
        UUID announcementId = createAnnouncement(adminToken, "Community event " + System.nanoTime());

        // Publish the announcement so resident can access it.
        mockMvc.perform(post("/api/announcements/" + announcementId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Create a resident user.
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phone = phoneFromUid(uid);
        UUID blockId = createBlock("AnnBlock-" + uid);
        UUID apartmentId = createApartment(blockId, "AR301");
        assignResident(phone, apartmentId);
        String residentToken = login(phone, "Password@123456");

        // First mark-read.
        mockMvc.perform(post("/api/announcements/" + announcementId + "/read")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRead").value(false))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        // Second mark-read — idempotent, alreadyRead=true.
        mockMvc.perform(post("/api/announcements/" + announcementId + "/read")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRead").value(true));
    }

    // =========================================================================
    // Test 4 — GET /api/announcements (RESIDENT) → only published announcements returned
    // =========================================================================

    @Test
    @DisplayName("GET /api/announcements — RESIDENT list returns only published announcements")
    void listAnnouncements_resident_onlyPublishedReturned() throws Exception {
        // Create a draft and a published announcement.
        String draftTitle  = "DRAFT-"     + System.nanoTime();
        String pubTitle    = "PUBLISHED-" + System.nanoTime();

        createAnnouncement(adminToken, draftTitle);
        UUID publishedId = createAnnouncement(adminToken, pubTitle);

        mockMvc.perform(post("/api/announcements/" + publishedId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Create and set up resident.
        String uid2 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phone2 = phoneFromUid(uid2);
        UUID blockId = createBlock("AnnBlock2-" + uid2);
        UUID apartmentId = createApartment(blockId, "AL301");
        assignResident(phone2, apartmentId);
        String residentToken = login(phone2, "Password@123456");

        MvcResult result = mockMvc.perform(get("/api/announcements")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data = (List<?>) body.get("data");

        // Every returned announcement must have publishedAt set.
        for (Object item : data) {
            Map<?, ?> ann = (Map<?, ?>) item;
            assertNotNull(ann.get("publishedAt"),
                    "Resident list must not contain unpublished announcements");
        }

        // The published announcement must appear in the list.
        boolean found = data.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(a -> publishedId.toString().equals(a.get("id")));
        assertTrue(found, "Published announcement must appear in resident list");

        // The draft must not appear.
        boolean draftFound = data.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(a -> draftTitle.equals(a.get("title")));
        assertTrue(!draftFound, "Draft announcement must not appear in resident list");
    }
}
