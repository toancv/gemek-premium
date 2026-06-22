/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementRepository;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Module 10 P2 — publish dispatch tests.
 *
 * <p>Verifies that {@code publishAnnouncement} creates exactly one in-app notification
 * row per scoped recipient (recipient set = {@code findRecipientUserIds}, the P1 query),
 * inside the same transaction, with correct row fields; and that the CAS publish guard
 * yields 409 CONFLICT on a second publish without creating duplicate rows.
 *
 * <p>Everything runs synchronously inside the test transaction — no async machinery,
 * no timing dependence. Class-level {@code @Transactional} rolls all fixtures back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnnouncementPublishDispatchTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Block blockA;
    private Block blockB;

    /** Active resident in block A floor 1. */
    private User userA1;

    /** Active resident in block A floor 2. */
    private User userA2;

    /** Active resident in block B floor 1. */
    private User userB1;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        blockA = saveBlock("P2A-" + tag);
        blockB = saveBlock("P2B-" + tag);

        Apartment aptA1 = saveApartment(blockA, (short) 1, "A1-" + tag);
        Apartment aptA2 = saveApartment(blockA, (short) 2, "A2-" + tag);
        Apartment aptB1 = saveApartment(blockB, (short) 1, "B1-" + tag);

        userA1 = saveUser(tag + 1);
        userA2 = saveUser(tag + 2);
        userB1 = saveUser(tag + 3);

        saveResident(userA1, aptA1);
        saveResident(userA2, aptA2);
        saveResident(userB1, aptB1);
    }

    // =========================================================================
    // Dispatch row count per scope
    // =========================================================================

    @Test
    @DisplayName("publish ALL — one notification row per findRecipientUserIds recipient, fields correct")
    void publishAllScope_createsOneRowPerRecipient() {
        Announcement draft = saveDraft(AnnouncementScope.ALL, null, null);

        AnnouncementResponse response =
                announcementService.publishAnnouncement(draft.getId(), userA1.getId());

        // publishedAt and rows exist together (atomic in one transaction).
        assertThat(response.getPublishedAt()).isNotNull();

        List<UUID> recipients = residentRepository.findRecipientUserIds(
                AnnouncementScope.ALL, null, null);
        assertThat(recipients).contains(userA1.getId(), userA2.getId(), userB1.getId());
        assertThat(countRowsFor(draft.getId())).isEqualTo(recipients.size());

        // Field assertions on one recipient's row.
        Notification row = rowFor(draft.getId(), userA1.getId());
        assertThat(row.getType()).isEqualTo(NotificationType.ANNOUNCEMENT_PUBLISHED);
        assertThat(row.getReferenceId()).isEqualTo(draft.getId());
        assertThat(row.getReferenceType()).isEqualTo("Announcement");
        assertThat(row.getTitle()).isEqualTo(draft.getTitle());
        assertThat(row.getBody()).isEqualTo("Có thông báo mới: " + draft.getTitle());
        assertThat(row.isRead()).isFalse();
    }

    @Test
    @DisplayName("publish BLOCK — only block A residents get rows")
    void publishBlockScope_createsRowsForBlockOnly() {
        Announcement draft = saveDraft(AnnouncementScope.BLOCK, blockA, null);

        announcementService.publishAnnouncement(draft.getId(), userA1.getId());

        List<UUID> recipients = residentRepository.findRecipientUserIds(
                AnnouncementScope.BLOCK, blockA.getId(), null);
        assertThat(recipients).contains(userA1.getId(), userA2.getId());
        assertThat(recipients).doesNotContain(userB1.getId());
        assertThat(countRowsFor(draft.getId())).isEqualTo(recipients.size());
    }

    @Test
    @DisplayName("publish FLOOR — only block A floor 1 residents get rows")
    void publishFloorScope_createsRowsForBlockAndFloorOnly() {
        Announcement draft = saveDraft(AnnouncementScope.FLOOR, blockA, (short) 1);

        announcementService.publishAnnouncement(draft.getId(), userA1.getId());

        List<UUID> recipients = residentRepository.findRecipientUserIds(
                AnnouncementScope.FLOOR, blockA.getId(), (short) 1);
        assertThat(recipients).contains(userA1.getId());
        assertThat(recipients).doesNotContain(userA2.getId(), userB1.getId());
        assertThat(countRowsFor(draft.getId())).isEqualTo(recipients.size());
    }

    // =========================================================================
    // 409 + no duplicate dispatch
    // =========================================================================

    @Test
    @DisplayName("second publish — 409 CONFLICT, notification row count unchanged")
    void publishTwice_secondThrowsConflictAndNoDuplicateRows() {
        Announcement draft = saveDraft(AnnouncementScope.ALL, null, null);

        announcementService.publishAnnouncement(draft.getId(), userA1.getId());
        long rowsAfterFirst = countRowsFor(draft.getId());
        assertThat(rowsAfterFirst).isGreaterThan(0);

        // CAS row-count 0 → CONFLICT; dispatch must not run again.
        assertThatThrownBy(() -> announcementService.publishAnnouncement(draft.getId(), userA1.getId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        assertThat(countRowsFor(draft.getId())).isEqualTo(rowsAfterFirst);
    }

    // =========================================================================
    // Unread count increments for a recipient
    // =========================================================================

    @Test
    @DisplayName("publish ALL — recipient unread count increments by exactly 1")
    void publish_incrementsRecipientUnreadCount() {
        long before = notificationRepository.countUnreadByUserId(userA1.getId());

        Announcement draft = saveDraft(AnnouncementScope.ALL, null, null);
        announcementService.publishAnnouncement(draft.getId(), userA1.getId());

        assertThat(notificationRepository.countUnreadByUserId(userA1.getId()))
                .isEqualTo(before + 1);
    }

    // =========================================================================
    // Query helpers
    // =========================================================================

    /**
     * Counts notification rows created for the given announcement.
     *
     * @param announcementId the announcement reference UUID.
     * @return number of notification rows referencing the announcement.
     */
    private long countRowsFor(UUID announcementId) {
        return entityManager.createQuery(
                        "SELECT COUNT(n) FROM Notification n WHERE n.referenceId = :id", Long.class)
                .setParameter("id", announcementId)
                .getSingleResult();
    }

    /**
     * Loads the single notification row for an announcement/user pair.
     *
     * @param announcementId the announcement reference UUID.
     * @param userId         the recipient user UUID.
     * @return the matching notification row.
     */
    private Notification rowFor(UUID announcementId, UUID userId) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id AND n.user.id = :userId",
                        Notification.class)
                .setParameter("id", announcementId)
                .setParameter("userId", userId)
                .getSingleResult();
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
     * Persists an active RESIDENT-role user with a unique phone.
     *
     * @param tag uniqueness tag for phone/name generation.
     * @return the saved user.
     */
    private User saveUser(long tag) {
        User user = new User();
        user.setPhone("07" + String.format("%09d", Math.abs(tag) % 1_000_000_000L));
        user.setFullName("P2 Fixture " + tag);
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
        return residentRepository.save(resident);
    }

    /**
     * Persists a DRAFT announcement (publishedAt null) with the given scope targeting.
     *
     * @param scope       the announcement scope.
     * @param targetBlock the target block for BLOCK/FLOOR scope, null for ALL.
     * @param targetFloor the target floor for FLOOR scope, null otherwise.
     * @return the saved draft announcement.
     */
    private Announcement saveDraft(AnnouncementScope scope, Block targetBlock, Short targetFloor) {
        Announcement announcement = new Announcement();
        announcement.setTitle("P2-" + scope + "-" + System.nanoTime());
        announcement.setContent("P2 dispatch fixture");
        announcement.setType(AnnouncementType.GENERAL);
        announcement.setScope(scope);
        announcement.setTargetBlock(targetBlock);
        announcement.setTargetFloor(targetFloor);
        return announcementRepository.save(announcement);
    }
}
