/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMedia;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMediaKind;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementMediaRepository;
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
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Integration coverage for the C2.3a detail media manifest on {@code GET /api/announcements/{id}}
 * ({@code AnnouncementService.getAnnouncement}). Proves the manifest carries FRESH presigned URLs
 * ONLY for media the caller may access — the access decision reuses the C2.1 presign gate, so an
 * out-of-scope resident gets the announcement text but an EMPTY manifest (no leak).
 *
 * <p>{@link FileStorageService} is mocked: {@code presign} returns a deterministic stub URL so the
 * test asserts the manifest wiring (which rows, which gate) without a live MinIO. Class-level
 * {@code @Transactional} rolls each fixture back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnnouncementDetailMediaManifestTest extends AbstractIntegrationTest {

    /** Mock MinIO — presign returns a stub so we assert manifest shape, not real signing. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementMediaRepository mediaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    /** Active resident in block A. */
    private User residentA;

    /** Published ALL-scope announcement with a cover + an inline image — residentA in scope. */
    private Announcement publishedAll;

    /** Published BLOCK-scope announcement targeting block B with media — residentA out of scope. */
    private Announcement publishedBlockB;

    /** DRAFT ALL-scope announcement with media — resident-invisible, ADMIN previewable. */
    private Announcement draftAll;

    @BeforeEach
    void setUp() {
        // presign is deterministic so assertions can check the minted URL maps to the right key.
        Mockito.when(fileStorageService.presign(anyString()))
                .thenAnswer(inv -> "https://minio.test/presigned?key=" + inv.getArgument(0));

        long tag = System.nanoTime();
        Block blockA = saveBlock("C23A-" + tag);
        Block blockB = saveBlock("C23B-" + tag);
        Apartment aptA1 = saveApartment(blockA, (short) 1, "u" + (tag % 1_000_000L));

        residentA = saveUser(tag + 1);
        saveResident(residentA, aptA1);

        publishedAll = saveAnnouncement(AnnouncementScope.ALL, null, null, OffsetDateTime.now());
        saveMedia(publishedAll, AnnouncementMediaKind.COVER);
        saveMedia(publishedAll, AnnouncementMediaKind.INLINE);

        publishedBlockB = saveAnnouncement(AnnouncementScope.BLOCK, blockB, null, OffsetDateTime.now());
        saveMedia(publishedBlockB, AnnouncementMediaKind.INLINE);

        draftAll = saveAnnouncement(AnnouncementScope.ALL, null, null, null);
        saveMedia(draftAll, AnnouncementMediaKind.COVER);
    }

    @Test
    @DisplayName("RESIDENT in scope: detail carries a manifest with a presigned URL per media row")
    void residentInScope_manifestPresent() {
        AnnouncementResponse res = announcementService.getAnnouncement(
                publishedAll.getId(), residentA.getId(), "RESIDENT");

        assertThat(res.getMedia()).hasSize(2);
        assertThat(res.getMedia()).allSatisfy(m -> {
            assertThat(m.getId()).isNotNull();
            assertThat(m.getUrl()).startsWith("https://minio.test/presigned?key=announcements/");
        });
        assertThat(res.getMedia()).anyMatch(m -> m.getKind() == AnnouncementMediaKind.COVER);
        assertThat(res.getMedia()).anyMatch(m -> m.getKind() == AnnouncementMediaKind.INLINE);
    }

    @Test
    @DisplayName("RESIDENT out of scope: detail text loads but the manifest is EMPTY (no leak)")
    void residentOutOfScope_manifestEmpty() {
        AnnouncementResponse res = announcementService.getAnnouncement(
                publishedBlockB.getId(), residentA.getId(), "RESIDENT");

        // The detail itself is reachable today, but no media URL is minted for an out-of-scope caller.
        assertThat(res.getMedia()).isEmpty();
        Mockito.verify(fileStorageService, Mockito.never()).presign(anyString());
    }

    @Test
    @DisplayName("ADMIN on a DRAFT: manifest present (authoring preview), draft media included")
    void adminDraft_manifestPresent() {
        AnnouncementResponse res = announcementService.getAnnouncement(
                draftAll.getId(), residentA.getId(), "ADMIN");

        assertThat(res.getMedia()).hasSize(1);
        assertThat(res.getMedia().get(0).getKind()).isEqualTo(AnnouncementMediaKind.COVER);
        assertThat(res.getMedia().get(0).getUrl()).contains("announcements/" + draftAll.getId());
    }

    @Test
    @DisplayName("Published announcement with no media: manifest is empty, no presign attempted")
    void publishedNoMedia_manifestEmpty() {
        Announcement bare = saveAnnouncement(AnnouncementScope.ALL, null, null, OffsetDateTime.now());

        AnnouncementResponse res = announcementService.getAnnouncement(
                bare.getId(), residentA.getId(), "RESIDENT");

        assertThat(res.getMedia()).isEmpty();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
        user.setFullName("C23 Fixture " + tag);
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

    private Announcement saveAnnouncement(AnnouncementScope scope, Block targetBlock,
                                          Short targetFloor, OffsetDateTime publishedAt) {
        Announcement announcement = new Announcement();
        announcement.setTitle("C23-" + scope + "-" + System.nanoTime());
        announcement.setContent("C2.3a manifest fixture");
        announcement.setType(AnnouncementType.GENERAL);
        announcement.setScope(scope);
        announcement.setTargetBlock(targetBlock);
        announcement.setTargetFloor(targetFloor);
        announcement.setPublishedAt(publishedAt);
        return announcementRepository.save(announcement);
    }

    private AnnouncementMedia saveMedia(Announcement announcement, AnnouncementMediaKind kind) {
        AnnouncementMedia media = new AnnouncementMedia();
        media.setAnnouncement(announcement);
        media.setObjectKey("announcements/" + announcement.getId() + "/" + UUID.randomUUID() + ".jpg");
        media.setContentType("image/jpeg");
        media.setSizeBytes(1024L);
        media.setKind(kind);
        media.setOriginalFilename("fixture.jpg");
        return mediaRepository.save(media);
    }
}
