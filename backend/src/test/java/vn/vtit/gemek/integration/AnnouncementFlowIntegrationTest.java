/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;
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
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import java.util.HashMap;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G4 cross-module integration tests for announcement publish/read flows.
 *
 * <p>Covers:
 * <ol>
 *   <li>Admin creates ALL-scope announcement, publishes it; resident reads the list, marks it read.</li>
 *   <li>BLOCK-scoped announcement: resident in target block sees it; resident in other block does not.</li>
 *   <li>Unpublished (draft) announcement is invisible to residents.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnouncementFlowIntegrationTest extends AbstractIntegrationTest {

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
    // Test 1 — create, publish, resident reads, mark-read → isRead=true
    // =========================================================================

    @Test
    @DisplayName("Admin creates ALL-scope announcement, publishes; resident sees it and marks as read")
    void createPublishAndResidentReads() throws Exception {
        String title = "AF1-" + System.nanoTime();
        UUID announcementId = createAnnouncement(adminToken, title, AnnouncementScope.ALL, null);

        // Admin publishes.
        MvcResult publishResult = mockMvc.perform(
                        post("/api/announcements/" + announcementId + "/publish")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> published = objectMapper.readValue(publishResult.getResponse().getContentAsString(), Map.class);
        assertNotNull(published.get("publishedAt"), "publishedAt must be set after publish");

        // Create resident.
        String residentToken = createResidentWithToken();

        // Resident's list must contain the published announcement.
        MvcResult listResult = mockMvc.perform(get("/api/announcements")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> listBody = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        List<?> data       = (List<?>) listBody.get("data");

        boolean found = data.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(a -> announcementId.toString().equals(a.get("id")));
        assertTrue(found, "Published announcement must appear in resident list");

        // Resident marks as read.
        mockMvc.perform(post("/api/announcements/" + announcementId + "/read")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRead").value(false))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        // Second read call must report alreadyRead=true.
        mockMvc.perform(post("/api/announcements/" + announcementId + "/read")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRead").value(true));
    }

    // =========================================================================
    // Test 2 — BLOCK-scoped announcement: only visible to residents in that block
    // =========================================================================

    @Test
    @DisplayName("BLOCK-scoped announcement: visible to resident in target block, invisible to other block")
    void blockScopedAnnouncement_onlyVisibleToBlock() throws Exception {
        // Create two blocks, one apartment each.
        UUID blockA    = createBlock("AFBlockA-" + System.nanoTime());
        UUID blockB    = createBlock("AFBlockB-" + System.nanoTime());
        UUID aptA      = createApartment(blockA, "AFA1");
        UUID aptB      = createApartment(blockB, "AFB1");

        String uidA  = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String uidB  = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tokenA = createResidentWithTokenForApartment(phoneFromUid(uidA), aptA);
        String tokenB = createResidentWithTokenForApartment(phoneFromUid(uidB), aptB);

        // Admin creates BLOCK-scoped announcement targeting block A.
        String title = "BlockAOnly-" + System.nanoTime();
        UUID announcementId = createAnnouncement(adminToken, title, AnnouncementScope.BLOCK, blockA);

        // Publish it.
        mockMvc.perform(post("/api/announcements/" + announcementId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Resident A (block A) must see the announcement.
        MvcResult listA = mockMvc.perform(get("/api/announcements")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        List<?> dataA = (List<?>) ((Map<?, ?>) objectMapper.readValue(
                listA.getResponse().getContentAsString(), Map.class)).get("data");
        boolean foundInA = dataA.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(a -> announcementId.toString().equals(a.get("id")));
        assertTrue(foundInA, "Resident in block A must see the BLOCK-scoped announcement");

        // Resident B (block B) must NOT see the announcement.
        MvcResult listB = mockMvc.perform(get("/api/announcements")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        List<?> dataB = (List<?>) ((Map<?, ?>) objectMapper.readValue(
                listB.getResponse().getContentAsString(), Map.class)).get("data");
        boolean foundInB = dataB.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(a -> announcementId.toString().equals(a.get("id")));
        assertFalse(foundInB, "Resident in block B must NOT see the block-A-scoped announcement");
    }

    // =========================================================================
    // Test 3 — unpublished announcement not visible to residents
    // =========================================================================

    @Test
    @DisplayName("Unpublished announcement is not visible to residents")
    void unpublishedAnnouncement_notVisibleToResident() throws Exception {
        String draftTitle = "DRAFT-AF3-" + System.nanoTime();
        createAnnouncement(adminToken, draftTitle, AnnouncementScope.ALL, null);

        // Create resident and check list — draft must not appear.
        String residentToken = createResidentWithToken();

        MvcResult result = mockMvc.perform(get("/api/announcements")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<?> data   = (List<?>) body.get("data");

        boolean draftFound = data.stream()
                .map(i -> (Map<?, ?>) i)
                .anyMatch(a -> draftTitle.equals(a.get("title")));
        assertFalse(draftFound, "Draft announcement must not appear in resident list");

        // Every returned announcement must have publishedAt set.
        for (Object item : data) {
            Map<?, ?> ann = (Map<?, ?>) item;
            assertNotNull(ann.get("publishedAt"),
                    "All announcements visible to resident must be published");
        }
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

    private UUID createUser(String phone) throws Exception {
        CreateUserRequest req = new CreateUserRequest(
                null, "Test Resident", phone, UserRole.RESIDENT, "Password@123456");
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
     * Creates a resident user with a new block+apartment, returns their JWT token.
     *
     * @return the resident JWT access token.
     */
    private String createResidentWithToken() throws Exception {
        String uid       = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID blockId     = createBlock("AFBlock-" + uid);
        UUID apartmentId = createApartment(blockId, "AF" + uid.substring(0, 6));
        return createResidentWithTokenForApartment(phoneFromUid(uid), apartmentId);
    }

    /**
     * Creates a resident user assigned to the given apartment, returns their JWT token.
     *
     * @param phone       the resident's phone number.
     * @param apartmentId the pre-existing apartment UUID to assign the resident to.
     * @return the resident JWT access token.
     */
    private String createResidentWithTokenForApartment(String phone, UUID apartmentId) throws Exception {
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
        return login(phone, "Password@123456");
    }

    private UUID createAnnouncement(String token, String title,
                                    AnnouncementScope scope, UUID targetBlockId) throws Exception {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest();
        req.setTitle(title);
        req.setContent("Content for: " + title);
        req.setType(AnnouncementType.GENERAL);
        req.setTargetScope(scope);
        if (targetBlockId != null) {
            req.setTargetBlockId(targetBlockId);
        }

        MvcResult result = mockMvc.perform(post("/api/announcements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }
}
