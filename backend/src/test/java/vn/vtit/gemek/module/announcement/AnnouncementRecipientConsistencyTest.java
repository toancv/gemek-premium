/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementRepository;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feed↔dispatch consistency contract test (Module 10, P1).
 *
 * <p>Invariant under test: for every announcement scope, a user is in the dispatch
 * recipient set ({@code ResidentRepository.findRecipientUserIds}) IF AND ONLY IF the
 * announcement appears in that user's resident feed
 * ({@code AnnouncementRepository.findPublishedForApartment} as composed by
 * {@code AnnouncementServiceImpl.listAnnouncements}). The two predicates are textual
 * mirrors; this test fails if either is ever edited without the other.
 *
 * <p>Feed-side model mirrors the service exactly: a user with no active residency gets
 * an empty feed, and a deactivated user account cannot log in, so it can never observe
 * the feed at all. Both must therefore be absent from the recipient set.
 *
 * <p>Runs against the shared compose test database, so assertions check membership of
 * fixture users only — never set equality (other rows may exist). The class-level
 * {@code @Transactional} rolls every fixture back after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnnouncementRecipientConsistencyTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    private Block blockA;
    private Block blockB;

    /** Active user, active resident in block A floor 1 — recipient for all three scopes. */
    private User userA1;

    /** Active user, active resident in block A floor 2 — ALL + BLOCK(A) only. */
    private User userA2;

    /** Active user, active resident in block B floor 1 — ALL only (wrong block for BLOCK/FLOOR). */
    private User userB1;

    /** Active user whose residency in block A floor 1 has ENDED (moveOutDate set) — never a recipient. */
    private User userMovedOut;

    /** DEACTIVATED user account with an active residency in block A floor 1 — never a recipient. */
    private User userDeactivated;

    /** RESIDENT-role user with NO resident row at all — never a recipient. */
    private User userNoApartment;

    private Announcement annAll;
    private Announcement annBlockA;
    private Announcement annFloorA1;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        blockA = saveBlock("P1A-" + tag);
        blockB = saveBlock("P1B-" + tag);

        Apartment aptA1 = saveApartment(blockA, (short) 1, "A1-" + tag);
        Apartment aptA2 = saveApartment(blockA, (short) 2, "A2-" + tag);
        Apartment aptB1 = saveApartment(blockB, (short) 1, "B1-" + tag);

        userA1          = saveUser(tag + 1, true);
        userA2          = saveUser(tag + 2, true);
        userB1          = saveUser(tag + 3, true);
        userMovedOut    = saveUser(tag + 4, true);
        userDeactivated = saveUser(tag + 5, false);
        userNoApartment = saveUser(tag + 6, true);

        saveResident(userA1, aptA1, null);
        saveResident(userA2, aptA2, null);
        saveResident(userB1, aptB1, null);
        saveResident(userMovedOut, aptA1, LocalDate.now().minusDays(1));
        saveResident(userDeactivated, aptA1, null);
        // userNoApartment intentionally gets no resident row.

        annAll     = saveAnnouncement(AnnouncementScope.ALL, null, null);
        annBlockA  = saveAnnouncement(AnnouncementScope.BLOCK, blockA, null);
        annFloorA1 = saveAnnouncement(AnnouncementScope.FLOOR, blockA, (short) 1);
    }

    // =========================================================================
    // Inverse-consistency invariant, per scope
    // =========================================================================

    @Test
    @DisplayName("ALL scope: recipient set ⟺ feed visibility for every fixture user")
    void allScopeConsistency() {
        List<UUID> recipients = residentRepository.findRecipientUserIds(
                AnnouncementScope.ALL, null, null);

        assertInvariantForAllFixtureUsers(recipients, annAll);

        // Explicit expectations on top of the invariant.
        assertTrue(recipients.contains(userA1.getId()), "active A1 resident must receive ALL");
        assertTrue(recipients.contains(userA2.getId()), "active A2 resident must receive ALL");
        assertTrue(recipients.contains(userB1.getId()), "active B1 resident must receive ALL");
        assertFalse(recipients.contains(userMovedOut.getId()), "moved-out resident excluded");
        assertFalse(recipients.contains(userDeactivated.getId()), "deactivated user excluded");
        assertFalse(recipients.contains(userNoApartment.getId()), "user without apartment excluded");
    }

    @Test
    @DisplayName("BLOCK scope: recipient set ⟺ feed visibility for every fixture user")
    void blockScopeConsistency() {
        List<UUID> recipients = residentRepository.findRecipientUserIds(
                AnnouncementScope.BLOCK, blockA.getId(), null);

        assertInvariantForAllFixtureUsers(recipients, annBlockA);

        assertTrue(recipients.contains(userA1.getId()), "block A floor 1 resident must receive BLOCK(A)");
        assertTrue(recipients.contains(userA2.getId()), "block A floor 2 resident must receive BLOCK(A)");
        assertFalse(recipients.contains(userB1.getId()), "block B resident excluded from BLOCK(A)");
        assertFalse(recipients.contains(userMovedOut.getId()), "moved-out resident excluded");
        assertFalse(recipients.contains(userDeactivated.getId()), "deactivated user excluded");
        assertFalse(recipients.contains(userNoApartment.getId()), "user without apartment excluded");
    }

    @Test
    @DisplayName("FLOOR scope: recipient set ⟺ feed visibility for every fixture user")
    void floorScopeConsistency() {
        List<UUID> recipients = residentRepository.findRecipientUserIds(
                AnnouncementScope.FLOOR, blockA.getId(), (short) 1);

        assertInvariantForAllFixtureUsers(recipients, annFloorA1);

        assertTrue(recipients.contains(userA1.getId()), "block A floor 1 resident must receive FLOOR(A,1)");
        assertFalse(recipients.contains(userA2.getId()), "block A floor 2 resident excluded from FLOOR(A,1)");
        assertFalse(recipients.contains(userB1.getId()),
                "block B floor 1 resident excluded — FLOOR requires block AND floor match");
        assertFalse(recipients.contains(userMovedOut.getId()), "moved-out resident excluded");
        assertFalse(recipients.contains(userDeactivated.getId()), "deactivated user excluded");
        assertFalse(recipients.contains(userNoApartment.getId()), "user without apartment excluded");
    }

    @Test
    @DisplayName("Recipient list contains no duplicate user IDs")
    void recipientsAreDistinct() {
        List<UUID> recipients = residentRepository.findRecipientUserIds(
                AnnouncementScope.ALL, null, null);
        assertEquals(recipients.size(), new HashSet<>(recipients).size(),
                "DISTINCT must collapse duplicate user IDs");
    }

    // =========================================================================
    // Invariant helper
    // =========================================================================

    /**
     * Asserts, for each fixture user, that recipient-set membership equals feed visibility.
     *
     * @param recipients   the dispatch recipient user-ID set under test.
     * @param announcement the published announcement whose feed visibility is checked.
     */
    private void assertInvariantForAllFixtureUsers(List<UUID> recipients, Announcement announcement) {
        // Loop over fixture users only — the shared DB may contain unrelated rows.
        for (User user : List.of(userA1, userA2, userB1, userMovedOut, userDeactivated, userNoApartment)) {
            boolean isRecipient = recipients.contains(user.getId());
            boolean seesInFeed  = feedSees(user, announcement);
            assertEquals(seesInFeed, isRecipient,
                    "feed↔dispatch divergence for user " + user.getFullName()
                            + " on scope " + announcement.getScope());
        }
    }

    /**
     * Models the resident feed exactly as {@code AnnouncementServiceImpl.listAnnouncements}:
     * no active residency → empty feed; deactivated account → cannot log in, sees nothing;
     * otherwise visibility comes from {@code findPublishedForApartment}.
     *
     * @param user         the fixture user whose feed is evaluated.
     * @param announcement the announcement to look for.
     * @return whether the user can see the announcement in their feed.
     */
    private boolean feedSees(User user, Announcement announcement) {
        // Deactivated accounts cannot authenticate — they can never observe the feed.
        if (!user.isActive()) {
            return false;
        }
        Optional<Resident> residency = residentRepository.findActiveByUserId(user.getId());
        // No active residency — the service returns an empty page.
        if (residency.isEmpty()) {
            return false;
        }
        Apartment apartment = residency.get().getApartment();
        return announcementRepository
                .findPublishedForApartment(apartment.getBlock().getId(), apartment.getFloor(), Pageable.unpaged())
                .getContent().stream()
                .anyMatch(a -> a.getId().equals(announcement.getId()));
    }

    // =========================================================================
    // Fixture helpers
    // =========================================================================

    /**
     * Persists a block with a unique name.
     *
     * @param name the unique block name.
     * @return the saved block.
     */
    private Block saveBlock(String name) {
        Block block = new Block();
        block.setName(name);
        return blockRepository.save(block);
    }

    /**
     * Persists an apartment in the given block and floor.
     *
     * @param block      the owning block.
     * @param floor      the floor number.
     * @param unitNumber the unit number, unique within the block.
     * @return the saved apartment.
     */
    private Apartment saveApartment(Block block, short floor, String unitNumber) {
        Apartment apartment = new Apartment();
        apartment.setBlock(block);
        apartment.setFloor(floor);
        apartment.setUnitNumber(unitNumber);
        return apartmentRepository.save(apartment);
    }

    /**
     * Persists a RESIDENT-role user with a unique phone.
     *
     * @param tag    uniqueness tag for phone/name generation.
     * @param active whether the account is active.
     * @return the saved user.
     */
    private User saveUser(long tag, boolean active) {
        User user = new User();
        user.setPhone("08" + String.format("%09d", Math.abs(tag) % 1_000_000_000L));
        user.setFullName("P1 Fixture " + tag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(UserRole.RESIDENT);
        user.setActive(active);
        return userRepository.save(user);
    }

    /**
     * Persists a residency linking user and apartment.
     *
     * @param user        the resident user.
     * @param apartment   the apartment.
     * @param moveOutDate null for an active residency, non-null for an ended one.
     * @return the saved resident row.
     */
    private Resident saveResident(User user, Apartment apartment, LocalDate moveOutDate) {
        Resident resident = new Resident();
        resident.setUser(user);
        resident.setApartment(apartment);
        resident.setType(ResidentType.OWNER);
        resident.setMoveInDate(LocalDate.now().minusYears(1));
        resident.setMoveOutDate(moveOutDate);
        return residentRepository.save(resident);
    }

    /**
     * Persists a PUBLISHED announcement with the given scope targeting.
     *
     * @param scope       the announcement scope.
     * @param targetBlock the target block for BLOCK/FLOOR scope, null for ALL.
     * @param targetFloor the target floor for FLOOR scope, null otherwise.
     * @return the saved announcement.
     */
    private Announcement saveAnnouncement(AnnouncementScope scope, Block targetBlock, Short targetFloor) {
        Announcement announcement = new Announcement();
        announcement.setTitle("P1-" + scope + "-" + System.nanoTime());
        announcement.setContent("P1 consistency fixture");
        announcement.setType(AnnouncementType.GENERAL);
        announcement.setScope(scope);
        announcement.setTargetBlock(targetBlock);
        announcement.setTargetFloor(targetFloor);
        announcement.setPublishedAt(OffsetDateTime.now());
        return announcementRepository.save(announcement);
    }
}
