/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
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
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Residency-lifecycle P3 — place-resident flow proofs (move-in / return / add-concurrent, keyed by phone).
 *
 * <p>Exercises {@code POST /api/residents} branching and {@code GET /api/residents/lookup} against the real
 * stack on the relaxed index (V20, {@code (user_id, apartment_id) WHERE move_out_date IS NULL}). Covers:
 * <ol>
 *   <li>NEW — unknown phone provisions user + residency.</li>
 *   <li>RETURNING — a moved-out (disabled) user is reactivated (enabled-only) and gets a fresh residency;
 *       the request's identity + password are IGNORED (server-derived identity, old credentials kept).</li>
 *   <li>ADD-CONCURRENT — an active user gains a 2nd active residency in a DIFFERENT apartment; account stays
 *       enabled; BOTH residencies active (the multi-residency creation path).</li>
 *   <li>confirmReuse=false on an existing phone → 409 REUSE_CONFIRMATION_REQUIRED, nothing created, matched
 *       info returned.</li>
 *   <li>Already active in the TARGET apartment → 409 ALREADY_ACTIVE_IN_APARTMENT, nothing created.</li>
 *   <li>Lookup returns the correct status + minimal PII per case.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class P3PlaceResidentIntegrationTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    private String adminToken;

    private static final String ADMIN_PHONE = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";
    private static final String RESIDENT_PASSWORD = "Password@123456";

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
    }

    // =========================================================================
    // 1 — NEW: unknown phone provisions a user + residency; the user can log in.
    // =========================================================================

    @Test
    @DisplayName("P3 NEW: unknown phone creates user + residency (201); new user can log in")
    void newPhone_createsUserAndResidency() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        UUID apt = createApartment(createBlock("P3-N-" + uid), "P3N-" + uid);

        placeResident(newResidentBody(phone, apt, "Người Mới " + uid))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.fullName").value("Người Mới " + uid))
                .andExpect(jsonPath("$.apartment.id").value(apt.toString()));

        UUID userId = userId(phone);
        Assertions.assertEquals(1, residentRepository.findAllActiveByUserId(userId).size());
        // The new user can authenticate (account active).
        login(phone, RESIDENT_PASSWORD);
    }

    // =========================================================================
    // 2 + 6 — RETURNING + IDOR: a moved-out/disabled user is reactivated (enabled-only) and gets a fresh
    //          residency; request identity + password are ignored; old credentials still work.
    // =========================================================================

    @Test
    @DisplayName("P3 RETURNING: reuse reactivates a disabled user, reuses identity, ignores smuggled identity/password")
    void returningUser_reactivatedIdentityReused() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        UUID apt = createApartment(createBlock("P3-R-" + uid), "P3R-" + uid);

        // Place, then move out → account is deactivated (no other residency).
        placeResident(newResidentBody(phone, apt, "Trần Văn Gốc")).andExpect(status().isCreated());
        UUID userId = userId(phone);
        UUID residentId = residentRepository.findAllActiveByUserId(userId).get(0).getId();
        moveOut(residentId);
        Assertions.assertFalse(userRepository.findById(userId).orElseThrow().isActive(),
                "after move-out with no other residency the account must be disabled");

        // Return: reuse with confirmReuse=true, smuggling a different name/password that MUST be ignored.
        Map<String, Object> body = newResidentBody(phone, apt, "HACKER NAME");
        body.put("password", "Hacker@999999");
        body.put("dateOfBirth", "2001-02-03");
        body.put("confirmReuse", true);
        placeResident(body).andExpect(status().isCreated());

        User reused = userRepository.findById(userId).orElseThrow();
        Assertions.assertTrue(reused.isActive(), "returning user account must be re-enabled");
        Assertions.assertEquals("Trần Văn Gốc", reused.getFullName(), "identity must NOT be overwritten by the request");
        // Old credentials still work; the smuggled password was never applied.
        login(phone, RESIDENT_PASSWORD);
        loginExpectFailure(phone, "Hacker@999999");
        // A fresh active residency exists; the old (moved-out) row remains in history.
        Assertions.assertEquals(1, residentRepository.findAllActiveByUserId(userId).size());
        Assertions.assertEquals(2, residentRepository.findAll().stream()
                .filter(r -> r.getUser().getId().equals(userId)).count(),
                "both the old moved-out row and the new active row must exist");
    }

    // =========================================================================
    // 3 — ADD-CONCURRENT: an active user gains a 2nd active residency in a DIFFERENT apartment.
    // =========================================================================

    @Test
    @DisplayName("P3 ADD-CONCURRENT: active user + confirmReuse adds a 2nd active residency in another apartment; both active, account enabled")
    void addConcurrent_secondActiveResidency() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        UUID aptA = createApartment(createBlock("P3-CA-" + uid), "P3CA-" + uid);
        UUID aptB = createApartment(createBlock("P3-CB-" + uid), "P3CB-" + uid);

        placeResident(newResidentBody(phone, aptA, "Lê Thị Đa")).andExpect(status().isCreated());
        UUID userId = userId(phone);

        Map<String, Object> body = newResidentBody(phone, aptB, "ignored");
        body.put("confirmReuse", true);
        placeResident(body).andExpect(status().isCreated());

        List<UUID> activeApts = residentRepository.findActiveApartmentIdsByUserId(userId);
        Assertions.assertEquals(2, activeApts.size(), "user must now hold 2 active residencies");
        Assertions.assertTrue(activeApts.contains(aptA) && activeApts.contains(aptB));
        Assertions.assertTrue(userRepository.findById(userId).orElseThrow().isActive(),
                "account stays enabled across the add-concurrent path");
    }

    // =========================================================================
    // 4 — confirmReuse=false on an existing phone → REUSE_CONFIRMATION_REQUIRED, nothing created.
    // =========================================================================

    @Test
    @DisplayName("P3 CONFIRM: existing phone + confirmReuse=false → 409 REUSE_CONFIRMATION_REQUIRED with matched info, nothing created")
    void existingPhoneNoConfirm_returnsConfirmationRequired() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        UUID aptA = createApartment(createBlock("P3-FA-" + uid), "P3FA-" + uid);
        UUID aptB = createApartment(createBlock("P3-FB-" + uid), "P3FB-" + uid);

        placeResident(newResidentBody(phone, aptA, "Phạm Văn Hiện")).andExpect(status().isCreated());
        UUID userId = userId(phone);

        placeResident(newResidentBody(phone, aptB, "ignored"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REUSE_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.matched.status").value("ACTIVE_ELSEWHERE"))
                .andExpect(jsonPath("$.matched.displayName").value("Phạm Văn Hiện"))
                .andExpect(jsonPath("$.matched.activeApartments[0].id").value(aptA.toString()))
                // PII discipline: the matched payload must NOT leak phone/email.
                .andExpect(jsonPath("$.matched.phone").doesNotExist())
                .andExpect(jsonPath("$.matched.email").doesNotExist());

        // Nothing created in apartment B.
        Assertions.assertEquals(1, residentRepository.findActiveApartmentIdsByUserId(userId).size());
    }

    // =========================================================================
    // 5 — already active in the TARGET apartment → 409 ALREADY_ACTIVE_IN_APARTMENT, nothing created.
    // =========================================================================

    @Test
    @DisplayName("P3 ALREADY-ACTIVE: existing phone already active in target apartment → 409, nothing created")
    void alreadyActiveInTarget_conflict() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        UUID apt = createApartment(createBlock("P3-AA-" + uid), "P3AA-" + uid);

        placeResident(newResidentBody(phone, apt, "Đỗ Văn Trùng")).andExpect(status().isCreated());
        UUID userId = userId(phone);

        Map<String, Object> body = newResidentBody(phone, apt, "ignored");
        body.put("confirmReuse", true);
        placeResident(body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_ACTIVE_IN_APARTMENT"));

        Assertions.assertEquals(1, residentRepository.findAllActiveByUserId(userId).size(),
                "no duplicate active residency in the same apartment");
    }

    // =========================================================================
    // 7 — lookup returns correct status + minimal PII per case.
    // =========================================================================

    @Test
    @DisplayName("P3 LOOKUP: NEW / ACTIVE_ELSEWHERE / ALREADY_HERE / MOVED_OUT statuses + minimal PII")
    void lookup_statusesAndMinimalPii() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        UUID aptA = createApartment(createBlock("P3-LA-" + uid), "P3LA-" + uid);

        // NEW — phone unused.
        lookup(phone, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.displayName").doesNotExist());

        placeResident(newResidentBody(phone, aptA, "Vũ Thị Tra")).andExpect(status().isCreated());

        // ACTIVE_ELSEWHERE — no target apartment supplied; minimal PII (no phone/email).
        lookup(phone, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE_ELSEWHERE"))
                .andExpect(jsonPath("$.displayName").value("Vũ Thị Tra"))
                .andExpect(jsonPath("$.activeApartments[0].id").value(aptA.toString()))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        // ALREADY_HERE — target apartment is the one the user resides in.
        lookup(phone, aptA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_HERE"));

        // MOVED_OUT — after move-out, no active residency.
        UUID userId = userId(phone);
        moveOut(residentRepository.findAllActiveByUserId(userId).get(0).getId());
        lookup(phone, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MOVED_OUT"))
                .andExpect(jsonPath("$.activeApartments").isEmpty());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

    private Map<String, Object> newResidentBody(String phone, UUID apartmentId, String fullName) {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", fullName);
        req.put("phone", phone);
        req.put("dateOfBirth", "1990-01-01");
        req.put("password", RESIDENT_PASSWORD);
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "OWNER");
        req.put("moveInDate", "2026-01-01");
        req.put("isPrimaryContact", false);
        return req;
    }

    private org.springframework.test.web.servlet.ResultActions placeResident(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/residents")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions lookup(String phone, UUID apartmentId) throws Exception {
        var req = get("/api/residents/lookup")
                .header("Authorization", "Bearer " + adminToken)
                .param("phone", phone);
        if (apartmentId != null) {
            req = req.param("apartmentId", apartmentId.toString());
        }
        return mockMvc.perform(req);
    }

    private void moveOut(UUID residentId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("moveOutDate", "2026-06-01");
        mockMvc.perform(post("/api/residents/" + residentId + "/move-out")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private UUID userId(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalStateException("user not found: " + phone))
                .getId();
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

    private void loginExpectFailure(String phone, String password) throws Exception {
        LoginRequest req = new LoginRequest(phone, password);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
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
}
