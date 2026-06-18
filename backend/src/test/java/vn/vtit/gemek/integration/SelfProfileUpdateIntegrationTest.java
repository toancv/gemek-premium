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
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the self-service profile-update endpoint
 * {@code PUT /api/auth/me/profile}.
 *
 * <p>Verifies the security-critical invariants of a principal-derived self-update:
 * <ol>
 *   <li>Happy path — caller updates own fullName/phone/email; {@code GET /api/auth/me} reflects it.</li>
 *   <li>IDOR/identity — the update touches ONLY the caller's row; a second user is untouched.</li>
 *   <li>Privilege-escalation guard — a crafted body carrying {@code role}/{@code isActive} must NOT
 *       change the caller's role or active flag.</li>
 *   <li>Phone uniqueness — another user's phone is rejected; the caller's OWN current phone (no change) succeeds.</li>
 *   <li>Email uniqueness — another user's email is rejected.</li>
 *   <li>Validation — malformed phone/email rejected with {@code VALIDATION_ERROR}.</li>
 *   <li>Token survival — the existing access token stays valid after a phone change (subject = UUID).</li>
 * </ol>
 *
 * <p>Each test creates its own users (unique phone/email) and asserts only on those rows,
 * so it is robust against shared-DB pollution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SelfProfileUpdateIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String adminToken;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";
    private static final String USER_PASSWORD  = "Password@123456";

    @BeforeEach
    void setUp() throws Exception {
        when(fileStorageService.presign(anyString())).thenReturn("http://minio/presigned-url");
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
    }

    // =========================================================================
    // Test 1 — happy path: caller updates own profile; GET /api/auth/me reflects it
    // =========================================================================

    @Test
    @DisplayName("PUT /api/auth/me/profile updates caller's own fullName/phone/email; GET /api/auth/me reflects the change")
    void updateOwnProfile_happyPath_persistsAndReflectsInGetMe() throws Exception {
        String uid = uid();
        String oldPhone = phoneFromUid(uid);
        createUser("Old Name", oldPhone, email(uid), UserRole.TECHNICIAN);
        String token = login(oldPhone, USER_PASSWORD);

        String newUid = uid();
        String newPhone = phoneFromUid(newUid);
        String newEmail = email(newUid);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "New Name");
        req.put("phone", newPhone);
        req.put("email", newEmail);

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.phone").value(newPhone))
                .andExpect(jsonPath("$.email").value(newEmail));

        // GET /api/auth/me must reflect the persisted change (same token still valid — subject is UUID).
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.phone").value(newPhone))
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    // =========================================================================
    // Test 2 — IDOR/identity: only the caller's row changes; a second user is untouched
    // =========================================================================

    @Test
    @DisplayName("PUT /api/auth/me/profile affects ONLY the caller's row — a second user is untouched (identity is principal-derived)")
    void updateOwnProfile_onlyAffectsCaller_secondUserUntouched() throws Exception {
        String uidA = uid();
        String phoneA = phoneFromUid(uidA);
        UUID idA = createUser("Caller A", phoneA, email(uidA), UserRole.TECHNICIAN);
        String tokenA = login(phoneA, USER_PASSWORD);

        String uidB = uid();
        String phoneB = phoneFromUid(uidB);
        String emailB = email(uidB);
        UUID idB = createUser("Bystander B", phoneB, emailB, UserRole.BOARD_MEMBER);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "Caller A Renamed");
        req.put("phone", phoneFromUid(uid()));
        req.put("email", email(uid()));

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Bystander B's row must be byte-for-byte unchanged.
        User b = userRepository.findById(idB).orElseThrow();
        assertEquals("Bystander B", b.getFullName(), "B fullName must be unchanged");
        assertEquals(phoneB, b.getPhone(), "B phone must be unchanged");
        assertEquals(emailB, b.getEmail(), "B email must be unchanged");

        // Caller A's row must reflect the rename.
        User a = userRepository.findById(idA).orElseThrow();
        assertEquals("Caller A Renamed", a.getFullName(), "A fullName must be updated");
    }

    // =========================================================================
    // Test 3 — PRIVILEGE-ESCALATION GUARD: role/isActive in the body must be ignored
    // =========================================================================

    @Test
    @DisplayName("PUT /api/auth/me/profile with role=ADMIN + isActive=false in the body does NOT change the caller's role or active flag")
    void updateOwnProfile_cannotSelfPromote_roleAndActiveImmutable() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        UUID id = createUser("Tech User", phone, email(uid), UserRole.TECHNICIAN);
        String token = login(phone, USER_PASSWORD);

        // Crafted body smuggling privilege fields the endpoint must NOT honour.
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "Tech User");
        req.put("phone", phone);
        req.put("email", email(uid));
        req.put("role", "ADMIN");
        req.put("isActive", false);
        req.put("id", UUID.randomUUID().toString());
        req.put("password", "Hacked@123456");

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // Response must report the UNCHANGED role/active.
                .andExpect(jsonPath("$.role").value("TECHNICIAN"))
                .andExpect(jsonPath("$.isActive").value(true));

        // Persisted row proves no escalation.
        User u = userRepository.findById(id).orElseThrow();
        assertEquals(UserRole.TECHNICIAN, u.getRole(), "Role must remain TECHNICIAN — no self-promotion");
        assertTrue(u.isActive(), "Active flag must remain true — body isActive=false ignored");
    }

    // =========================================================================
    // Test 4 — phone uniqueness: another user's phone rejected; OWN phone (no change) succeeds
    // =========================================================================

    @Test
    @DisplayName("PUT /api/auth/me/profile changing phone to ANOTHER user's phone → PHONE_ALREADY_EXISTS")
    void updateOwnProfile_phoneTakenByAnotherUser_rejected() throws Exception {
        String uidA = uid();
        String phoneA = phoneFromUid(uidA);
        createUser("Caller A", phoneA, email(uidA), UserRole.TECHNICIAN);
        String tokenA = login(phoneA, USER_PASSWORD);

        String uidB = uid();
        String phoneB = phoneFromUid(uidB);
        createUser("Bystander B", phoneB, email(uidB), UserRole.TECHNICIAN);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "Caller A");
        req.put("phone", phoneB);           // collide with B
        req.put("email", email(uid()));

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PHONE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("PUT /api/auth/me/profile setting phone to the caller's OWN current phone (no change) succeeds — self excluded from uniqueness")
    void updateOwnProfile_phoneUnchanged_selfExcluded_succeeds() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        createUser("Same Phone", phone, email(uid), UserRole.TECHNICIAN);
        String token = login(phone, USER_PASSWORD);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "Same Phone Renamed");
        req.put("phone", phone);            // unchanged — must NOT trip uniqueness
        req.put("email", email(uid));

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.fullName").value("Same Phone Renamed"));
    }

    // =========================================================================
    // Test 5 — email uniqueness: another user's email rejected
    // =========================================================================

    @Test
    @DisplayName("PUT /api/auth/me/profile changing email to ANOTHER user's email → EMAIL_ALREADY_EXISTS")
    void updateOwnProfile_emailTakenByAnotherUser_rejected() throws Exception {
        String uidA = uid();
        String phoneA = phoneFromUid(uidA);
        createUser("Caller A", phoneA, email(uidA), UserRole.TECHNICIAN);
        String tokenA = login(phoneA, USER_PASSWORD);

        String uidB = uid();
        String emailB = email(uidB);
        createUser("Bystander B", phoneFromUid(uidB), emailB, UserRole.TECHNICIAN);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "Caller A");
        req.put("phone", phoneA);           // own phone — no phone collision
        req.put("email", emailB);           // collide with B's email

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }

    // =========================================================================
    // Test 6 — validation: malformed phone / email rejected with VALIDATION_ERROR
    // =========================================================================

    @Test
    @DisplayName("PUT /api/auth/me/profile with malformed phone → 400 VALIDATION_ERROR")
    void updateOwnProfile_malformedPhone_rejected() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        createUser("Bad Phone", phone, email(uid), UserRole.TECHNICIAN);
        String token = login(phone, USER_PASSWORD);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "Bad Phone");
        req.put("phone", "12345");          // not a valid VN mobile
        req.put("email", email(uid));

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /api/auth/me/profile with malformed email → 400 VALIDATION_ERROR")
    void updateOwnProfile_malformedEmail_rejected() throws Exception {
        String uid = uid();
        String phone = phoneFromUid(uid);
        createUser("Bad Email", phone, email(uid), UserRole.TECHNICIAN);
        String token = login(phone, USER_PASSWORD);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("fullName", "Bad Email");
        req.put("phone", phone);
        req.put("email", "not-an-email");   // fails @Email

        mockMvc.perform(put("/api/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** Derives a valid VN mobile ({@code 090xxxxxxx}) from a hex uid — same scheme as the lifecycle suite. */
    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

    private static String email(String uid) {
        return "selfprofile-" + uid + "@gemek.vn";
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

    private UUID createUser(String fullName, String phone, String emailAddr, UserRole role) throws Exception {
        CreateUserRequest req = new CreateUserRequest(emailAddr, fullName, phone, role, USER_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }
}
