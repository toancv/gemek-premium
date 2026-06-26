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
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementAttachment;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementAttachmentRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the C3 detail ATTACHMENT manifest scope gate
 * ({@code AnnouncementServiceImpl.buildAttachmentManifest}) — it reuses the C2.1
 * {@code assertMediaPresignAccess} rule, so an out-of-scope resident must get an EMPTY
 * {@code attachments[]} while an in-scope resident and ADMIN get it populated.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnnouncementAttachmentManifestScopeTest extends AbstractIntegrationTest {

    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementAttachmentRepository attachmentRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    private User residentA;
    private Announcement publishedAll;
    private Announcement publishedBlockB;
    private Announcement draftAll;

    @BeforeEach
    void setUp() {
        when(fileStorageService.presign(any(), any(), any())).thenReturn("http://localhost:8090/dl");
        long tag = System.nanoTime();
        Block blockA = saveBlock("C3A-" + tag);
        Block blockB = saveBlock("C3B-" + tag);
        Apartment aptA1 = saveApartment(blockA, (short) 1, "u" + (tag % 1_000_000L));
        residentA = saveUser(tag + 1);
        saveResident(residentA, aptA1);

        publishedAll = saveAnnouncement(AnnouncementScope.ALL, null, OffsetDateTime.now());
        publishedBlockB = saveAnnouncement(AnnouncementScope.BLOCK, blockB, OffsetDateTime.now());
        draftAll = saveAnnouncement(AnnouncementScope.ALL, null, null);
        seedAttachment(publishedAll.getId());
        seedAttachment(publishedBlockB.getId());
        seedAttachment(draftAll.getId());
    }

    @Test
    @DisplayName("In-scope RESIDENT gets a populated attachments[] for a published announcement")
    void residentInScope_populated() {
        AnnouncementResponse res = announcementService.getAnnouncement(
                publishedAll.getId(), residentA.getId(), "RESIDENT");
        assertThat(res.getAttachments()).hasSize(1);
        assertThat(res.getAttachments().get(0).getDownloadUrl()).isEqualTo("http://localhost:8090/dl");
    }

    @Test
    @DisplayName("Out-of-scope RESIDENT gets an EMPTY attachments[] (no leak) for a published announcement")
    void residentOutOfScope_empty() {
        AnnouncementResponse res = announcementService.getAnnouncement(
                publishedBlockB.getId(), residentA.getId(), "RESIDENT");
        assertThat(res.getAttachments()).isEmpty();
    }

    @Test
    @DisplayName("ADMIN gets a populated attachments[] for a DRAFT announcement (authoring)")
    void adminDraft_populated() {
        AnnouncementResponse res = announcementService.getAnnouncement(
                draftAll.getId(), UUID.randomUUID(), "ADMIN");
        assertThat(res.getAttachments()).hasSize(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void seedAttachment(UUID announcementId) {
        AnnouncementAttachment a = new AnnouncementAttachment();
        a.setAnnouncement(announcementRepository.getReferenceById(announcementId));
        a.setObjectKey("announcements/" + announcementId + "/" + UUID.randomUUID() + ".pdf");
        a.setContentType("application/pdf");
        a.setSizeBytes(123L);
        a.setDisplayFilename("scope.pdf");
        attachmentRepository.save(a);
    }

    private Block saveBlock(String name) {
        Block block = new Block();
        block.setName(name);
        return blockRepository.save(block);
    }

    private Apartment saveApartment(Block block, short floor, String unitNumber) {
        Apartment apartment = new Apartment();
        apartment.setBlock(block);
        apartment.setFloor(floor);
        apartment.setUnitNumber(unitNumber);
        return apartmentRepository.save(apartment);
    }

    private User saveUser(long tag) {
        User user = new User();
        user.setPhone("08" + String.format("%09d", Math.abs(tag) % 1_000_000_000L));
        user.setFullName("C3 Fixture " + tag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(UserRole.RESIDENT);
        user.setActive(true);
        return userRepository.save(user);
    }

    private Resident saveResident(User user, Apartment apartment) {
        Resident resident = new Resident();
        resident.setUser(user);
        resident.setApartment(apartment);
        resident.setType(ResidentType.OWNER);
        resident.setMoveInDate(LocalDate.now().minusYears(1));
        resident.setMoveOutDate(null);
        return residentRepository.save(resident);
    }

    private Announcement saveAnnouncement(AnnouncementScope scope, Block targetBlock, OffsetDateTime publishedAt) {
        Announcement announcement = new Announcement();
        announcement.setTitle("C3-scope-" + scope + "-" + System.nanoTime());
        announcement.setContent("C3 attachment scope fixture");
        announcement.setType(AnnouncementType.GENERAL);
        announcement.setScope(scope);
        announcement.setTargetBlock(targetBlock);
        announcement.setPublishedAt(publishedAt);
        return announcementRepository.save(announcement);
    }
}
