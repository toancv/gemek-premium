/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.ticket.TicketService;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hardening H2 — prefix-routed presign access (API-SPEC §13 access matrix, ruling E3).
 *
 * <p>Covers: {@code announcements/} keys routed to the C2.1 scope-mirroring gate
 * (ADMIN/BOARD unrestricted, outsider RESIDENT/TECHNICIAN denied, legacy id-less key
 * malformed → denied), unauthenticated request rejected with 401 by the security
 * filter, unknown prefixes denied 403 by default. Deep scope/draft semantics live in
 * {@code AnnouncementMediaPresignAccessTest}; this class only guards the dispatch routing.
 * The {@code tickets/} branch is regression-guarded by {@code TicketPublicAccessTest}
 * (household/staff rule, presign-denied heart-pair) — only the 404-on-unknown-key path
 * is re-asserted here.
 *
 * <p>Class-level {@code @Transactional} rolls all fixtures back.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PresignPrefixRoutingTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    private User resident;
    private User technician;
    private User admin;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();
        // Phone prefix "01" — unused by other test classes (02/04/05/06/07/08 taken).
        resident = saveUser(tag + 1, UserRole.RESIDENT);
        technician = saveUser(tag + 2, UserRole.TECHNICIAN);
        admin = saveUser(tag + 3, UserRole.ADMIN);
    }

    // =========================================================================
    // announcements/ — C2.1 scope-mirroring gate (replaces the any-authenticated stub)
    // =========================================================================

    @Test
    @DisplayName("announcements/ key — ADMIN unrestricted; outsider RESIDENT and TECHNICIAN denied")
    void announcementPrefix_scopeMirrored_outsiderDenied_adminAllowed() {
        // Well-formed C2.1 media key: announcements/{announcementId}/{file}. The id is random
        // (no row) — ADMIN is unrestricted so it still passes the access check; the outsider
        // resident (NO residency) and the technician (no announcement audience) are denied.
        String key = "announcements/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".jpg";

        assertThatCode(() -> ticketService.assertPresignAccess(key, admin.getId(), "ADMIN"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ticketService.assertPresignAccess(key, resident.getId(), "RESIDENT"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> ticketService.assertPresignAccess(key, technician.getId(), "TECHNICIAN"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("announcements/ legacy id-less key — malformed, denied even for ADMIN (no 500)")
    void announcementPrefix_legacyKeyWithoutId_isForbidden() {
        // The pre-C2.1 shape announcements/{uuid}.jpg has no {announcementId} segment → malformed → deny.
        String legacyKey = "announcements/" + UUID.randomUUID() + ".jpg";
        assertThatThrownBy(() -> ticketService.assertPresignAccess(legacyKey, admin.getId(), "ADMIN"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("announcements/ key — unauthenticated HTTP request rejected with 401")
    void announcementPrefix_unauthenticated_is401() throws Exception {
        mockMvc.perform(get("/api/files/presign")
                        .param("key", "announcements/" + UUID.randomUUID() + ".jpg"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Unknown prefixes — deny by default
    // =========================================================================

    @Test
    @DisplayName("unknown prefixes (contracts/, bare UUID, empty) — 403 deny-by-default, even for admin")
    void unknownPrefixes_areForbidden() {
        for (String key : new String[]{
                "contracts/" + UUID.randomUUID() + ".pdf",
                UUID.randomUUID().toString(),
                ""}) {
            assertThatThrownBy(() -> ticketService.assertPresignAccess(key, admin.getId(), "ADMIN"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    // =========================================================================
    // tickets/ — branch intact (full rule covered by TicketPublicAccessTest)
    // =========================================================================

    @Test
    @DisplayName("tickets/ key with no photo row — still 404 (B-05 path unchanged)")
    void ticketPrefix_unknownKey_is404() {
        assertThatThrownBy(() -> ticketService.assertPresignAccess(
                "tickets/" + UUID.randomUUID() + "/before/" + UUID.randomUUID() + ".jpg",
                admin.getId(), "ADMIN"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /**
     * Persists an active user with the given role and a unique "01"-prefixed phone.
     *
     * @param tag  uniqueness tag.
     * @param role the role to assign.
     * @return the saved user.
     */
    private User saveUser(long tag, UserRole role) {
        User user = new User();
        user.setPhone("01" + String.format("%08d", Math.abs(tag) % 100_000_000L));
        user.setFullName("H2 " + role + " " + tag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }
}
