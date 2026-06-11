/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.model.PageResponse;
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
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module 10 P4 — per-user {@code isRead} on {@link AnnouncementResponse}.
 *
 * <p>Verifies: detail flag flips false→true after markRead; the batched per-page
 * read-state query maps each row independently (mixed page); and the admin
 * {@code findAll} list path still works after the {@code toResponse} change.
 *
 * <p>Class-level {@code @Transactional} rolls all fixtures back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnnouncementIsReadTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    /** Active resident in the fixture block, floor 1. */
    private User resident;

    /** ADMIN-role user with no residency. */
    private User admin;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        Block block = saveBlock("P4-" + tag);
        Apartment apartment = saveApartment(block, (short) 1, "P4A-" + tag);

        resident = saveUser(tag + 1, UserRole.RESIDENT);
        admin = saveUser(tag + 2, UserRole.ADMIN);
        saveResident(resident, apartment);
    }

    // =========================================================================
    // Detail — flag flips after markRead
    // =========================================================================

    @Test
    @DisplayName("getAnnouncement — isRead false before markRead, true after")
    void getAnnouncement_isReadFlipsAfterMarkRead() {
        Announcement published = savePublished("P4-detail-");

        AnnouncementResponse before = announcementService.getAnnouncement(
                published.getId(), resident.getId(), "RESIDENT");
        assertThat(before.isRead()).isFalse();

        announcementService.markRead(published.getId(), resident.getId());

        AnnouncementResponse after = announcementService.getAnnouncement(
                published.getId(), resident.getId(), "RESIDENT");
        assertThat(after.isRead()).isTrue();
    }

    // =========================================================================
    // Resident list — batched query maps each row independently
    // =========================================================================

    @Test
    @DisplayName("listAnnouncements RESIDENT — mixed page: read row true, unread row false")
    void residentList_mixedPage_perRowFlags() {
        Announcement readOne = savePublished("P4-read-");
        Announcement unreadOne = savePublished("P4-unread-");

        announcementService.markRead(readOne.getId(), resident.getId());

        PageResponse<AnnouncementResponse> page = announcementService.listAnnouncements(
                resident.getId(), "RESIDENT", pageable());

        assertThat(flagFor(page, readOne.getId())).contains(true);
        assertThat(flagFor(page, unreadOne.getId())).contains(false);
    }

    // =========================================================================
    // Admin list — findAll path unaffected by the toResponse change
    // =========================================================================

    @Test
    @DisplayName("listAnnouncements ADMIN — findAll path works, admin's own isRead computed")
    void adminList_findAllPath_isReadComputedForAdmin() {
        Announcement published = savePublished("P4-admin-");

        PageResponse<AnnouncementResponse> before = announcementService.listAnnouncements(
                admin.getId(), "ADMIN", pageable());
        assertThat(flagFor(before, published.getId())).contains(false);

        announcementService.markRead(published.getId(), admin.getId());

        PageResponse<AnnouncementResponse> after = announcementService.listAnnouncements(
                admin.getId(), "ADMIN", pageable());
        assertThat(flagFor(after, published.getId())).contains(true);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds the page request used by all list assertions — newest first so the
     * just-created fixtures land on page 0 even against a shared test database.
     *
     * @return page 0, size 50, createdAt descending.
     */
    private static Pageable pageable() {
        return PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Finds the {@code isRead} flag of a given announcement within a page.
     *
     * @param page page response to search.
     * @param id   announcement UUID to find.
     * @return optional flag — empty if the announcement is not on the page.
     */
    private static Optional<Boolean> flagFor(PageResponse<AnnouncementResponse> page, UUID id) {
        return page.getData().stream()
                .filter(a -> a.getId().equals(id))
                .map(AnnouncementResponse::isRead)
                .findFirst();
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
     * Persists an active user with a unique phone.
     *
     * @param tag  uniqueness tag for phone/name generation.
     * @param role the user role.
     * @return the saved user.
     */
    private User saveUser(long tag, UserRole role) {
        User user = new User();
        user.setPhone("06" + String.format("%09d", Math.abs(tag) % 1_000_000_000L));
        user.setFullName("P4 Fixture " + tag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    /**
     * Persists an active residency linking user and apartment.
     *
     * @param user      the resident user.
     * @param apartment the apartment.
     */
    private void saveResident(User user, Apartment apartment) {
        Resident residentRow = new Resident();
        residentRow.setUser(user);
        residentRow.setApartment(apartment);
        residentRow.setType(ResidentType.OWNER);
        residentRow.setMoveInDate(LocalDate.now().minusYears(1));
        residentRepository.save(residentRow);
    }

    /**
     * Persists an already-published ALL-scope announcement.
     *
     * @param titlePrefix unique title prefix.
     * @return the saved published announcement.
     */
    private Announcement savePublished(String titlePrefix) {
        Announcement announcement = new Announcement();
        announcement.setTitle(titlePrefix + System.nanoTime());
        announcement.setContent("P4 isRead fixture");
        announcement.setType(AnnouncementType.GENERAL);
        announcement.setScope(AnnouncementScope.ALL);
        announcement.setPublishedAt(OffsetDateTime.now());
        return announcementRepository.save(announcement);
    }
}
