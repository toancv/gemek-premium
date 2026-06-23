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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration coverage for the C2.1 announcement-media presign gate
 * ({@code AnnouncementService.assertMediaPresignAccess}) against the real DB and JPQL.
 *
 * <p>This is the security gate landed BEFORE any announcement image upload exists (C2.2):
 * it proves the formerly any-authenticated stub is replaced by a check that MIRRORS the
 * announcement read scope. The published-only / scope semantics live in
 * {@code AnnouncementRepository.existsReadableByResident}; the unit test
 * ({@code AnnouncementServiceImplTest}) covers role routing and malformed-key parsing with mocks,
 * while this test exercises the real scope predicate end-to-end.
 *
 * <p>No real media object is created (C2.2 introduces uploads). Object keys are synthesized per the
 * defined convention {@code announcements/{announcementId}/{uuid}} so the gate can be exercised today.
 * Class-level {@code @Transactional} rolls each fixture back; assertions reference fixture rows only.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnnouncementMediaPresignAccessTest extends AbstractIntegrationTest {

    /** Mock MinIO — the gate never touches storage; it only authorizes. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private AnnouncementService announcementService;

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

    /** Active resident in block A — in scope for ALL and BLOCK(A), out of scope for BLOCK(B). */
    private User residentA;

    /** Published ALL-scope announcement — every active resident may read its media. */
    private Announcement publishedAll;

    /** Published BLOCK-scope announcement targeting block B — residentA is OUT of scope. */
    private Announcement publishedBlockB;

    /** DRAFT ALL-scope announcement (publishedAt null) — resident media access denied. */
    private Announcement draftAll;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        Block blockA = saveBlock("C21A-" + tag);
        Block blockB = saveBlock("C21B-" + tag);
        // unit_number is varchar(20) — keep it short (block name has more room).
        Apartment aptA1 = saveApartment(blockA, (short) 1, "u" + (tag % 1_000_000L));

        residentA = saveUser(tag + 1);
        saveResident(residentA, aptA1);

        publishedAll = saveAnnouncement(AnnouncementScope.ALL, null, null, OffsetDateTime.now());
        publishedBlockB = saveAnnouncement(AnnouncementScope.BLOCK, blockB, null, OffsetDateTime.now());
        draftAll = saveAnnouncement(AnnouncementScope.ALL, null, null, null);
    }

    // =========================================================================
    // RESIDENT — scope mirror (published + ALL/BLOCK/FLOOR match)
    // =========================================================================

    @Test
    @DisplayName("RESIDENT in scope CAN presign a published announcement's media key")
    void residentInScope_allowed() {
        assertThatCode(() -> announcementService.assertMediaPresignAccess(
                mediaKey(publishedAll.getId()), residentA.getId(), "RESIDENT"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RESIDENT out of scope CANNOT presign (block B media, resident in block A)")
    void residentOutOfScope_denied() {
        assertForbidden(() -> announcementService.assertMediaPresignAccess(
                mediaKey(publishedBlockB.getId()), residentA.getId(), "RESIDENT"));
    }

    @Test
    @DisplayName("RESIDENT CANNOT presign a DRAFT announcement's media key")
    void residentDraft_denied() {
        assertForbidden(() -> announcementService.assertMediaPresignAccess(
                mediaKey(draftAll.getId()), residentA.getId(), "RESIDENT"));
    }

    @Test
    @DisplayName("RESIDENT key for a nonexistent announcement is DENIED, not a 500")
    void residentNonexistent_denied() {
        assertForbidden(() -> announcementService.assertMediaPresignAccess(
                mediaKey(UUID.randomUUID()), residentA.getId(), "RESIDENT"));
    }

    // =========================================================================
    // ADMIN / BOARD — unrestricted, including draft media (authoring preview)
    // =========================================================================

    @Test
    @DisplayName("ADMIN can presign a DRAFT announcement's media key (unrestricted)")
    void adminDraft_allowed() {
        assertThatCode(() -> announcementService.assertMediaPresignAccess(
                mediaKey(draftAll.getId()), residentA.getId(), "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BOARD_MEMBER can presign a published announcement's media key (unrestricted)")
    void boardPublished_allowed() {
        assertThatCode(() -> announcementService.assertMediaPresignAccess(
                mediaKey(publishedAll.getId()), residentA.getId(), "BOARD_MEMBER"))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Synthesizes a media object key per the C2.1 convention announcements/{id}/{file}.
     *
     * @param announcementId the owning announcement id.
     * @return a well-formed media object key.
     */
    private static String mediaKey(UUID announcementId) {
        return "announcements/" + announcementId + "/" + UUID.randomUUID() + ".jpg";
    }

    /**
     * Asserts the runnable throws an {@link AppException} carrying {@link ErrorCode#FORBIDDEN}.
     *
     * @param runnable the access call expected to be denied.
     */
    private static void assertForbidden(org.junit.jupiter.api.function.Executable runnable) {
        assertThatThrownBy(runnable::execute)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

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
     * Persists an active RESIDENT-role user with a unique phone.
     *
     * @param tag uniqueness tag for phone/name generation.
     * @return the saved user.
     */
    private User saveUser(long tag) {
        User user = new User();
        user.setPhone("08" + String.format("%09d", Math.abs(tag) % 1_000_000_000L));
        user.setFullName("C21 Fixture " + tag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(UserRole.RESIDENT);
        user.setActive(true);
        return userRepository.save(user);
    }

    /**
     * Persists an active residency linking user and apartment.
     *
     * @param user      the resident user.
     * @param apartment the apartment.
     * @return the saved resident row.
     */
    private Resident saveResident(User user, Apartment apartment) {
        Resident resident = new Resident();
        resident.setUser(user);
        resident.setApartment(apartment);
        resident.setType(ResidentType.OWNER);
        resident.setMoveInDate(LocalDate.now().minusYears(1));
        resident.setMoveOutDate(null);
        return residentRepository.save(resident);
    }

    /**
     * Persists an announcement with the given scope targeting and publish state.
     *
     * @param scope       the announcement scope.
     * @param targetBlock the target block for BLOCK/FLOOR scope, null for ALL.
     * @param targetFloor the target floor for FLOOR scope, null otherwise.
     * @param publishedAt the publish timestamp, or null for a draft.
     * @return the saved announcement.
     */
    private Announcement saveAnnouncement(AnnouncementScope scope, Block targetBlock,
                                          Short targetFloor, OffsetDateTime publishedAt) {
        Announcement announcement = new Announcement();
        announcement.setTitle("C21-" + scope + "-" + System.nanoTime());
        announcement.setContent("C2.1 media-access fixture");
        announcement.setType(AnnouncementType.GENERAL);
        announcement.setScope(scope);
        announcement.setTargetBlock(targetBlock);
        announcement.setTargetFloor(targetFloor);
        announcement.setPublishedAt(publishedAt);
        return announcementRepository.save(announcement);
    }
}
