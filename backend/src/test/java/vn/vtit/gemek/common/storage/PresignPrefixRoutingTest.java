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
 * <p>Covers: {@code announcements/} keys presignable by ANY authenticated role
 * (broadcast content), unauthenticated request rejected with 401 by the security
 * filter, unknown prefixes denied 403 by default. The {@code tickets/} branch is
 * regression-guarded by {@code TicketPublicAccessTest} (household/staff rule,
 * presign-denied heart-pair) — only the 404-on-unknown-key path is re-asserted here.
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
    // announcements/ — any authenticated role may presign (E3)
    // =========================================================================

    @Test
    @DisplayName("announcements/ key — outsider resident, technician and admin all pass the access check")
    void announcementPrefix_allAuthenticatedRoles_pass() {
        String key = "announcements/" + UUID.randomUUID() + ".jpg";

        // The fixture resident has NO residency row at all — the most outsider a
        // resident can be; the announcement surface must not care.
        assertThatCode(() -> ticketService.assertPresignAccess(key, resident.getId(), "RESIDENT"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ticketService.assertPresignAccess(key, technician.getId(), "TECHNICIAN"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ticketService.assertPresignAccess(key, admin.getId(), "ADMIN"))
                .doesNotThrowAnyException();
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
