/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.announcement.dto.AnnouncementMediaResponse;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMedia;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMediaKind;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementMediaRepository;
import vn.vtit.gemek.module.announcement.repository.AnnouncementRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Service-layer integration coverage for C2.2 announcement media upload/list/delete against the
 * real DB and JPQL, with MinIO mocked.
 *
 * <p>Deliberately NOT {@code @Transactional}: the after-commit MinIO cleanup
 * ({@code @TransactionalEventListener(AFTER_COMMIT)}) only fires on a real commit, so each test
 * commits and {@link #cleanup()} removes the created announcements (media rows go via FK CASCADE).
 * Controller security / HTTP status is covered separately by {@code AnnouncementControllerTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnnouncementMediaServiceIntegrationTest extends AbstractIntegrationTest {

    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    /** Minimal valid JPEG (SOI + JFIF APP0 + EOI). */
    private static final byte[] JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            'J', 'F', 'I', 'F', 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xD9};

    /** PNG 8-byte signature + a stub IHDR length — enough for Tika to detect image/png. */
    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n',
            0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'};

    /** RIFF....WEBP container header — Tika detects image/webp. */
    private static final byte[] WEBP_BYTES = new byte[]{
            'R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P',
            'V', 'P', '8', ' ', 0x0E, 0x00, 0x00, 0x00};

    /** GIF89a header — a real image, but a DISALLOWED type. */
    private static final byte[] GIF_BYTES = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00, 0x01, 0x00};

    /** Plain text masquerading as a .jpg — Tika sees text/plain and rejects it. */
    private static final byte[] TEXT_BYTES = "this is definitely not an image".getBytes();

    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementMediaRepository mediaRepository;

    private final List<UUID> createdAnnouncementIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // Non-transactional: remove what each test committed (media rows cascade with the announcement).
        for (UUID id : createdAnnouncementIds) {
            announcementRepository.findById(id).ifPresent(announcementRepository::delete);
        }
        createdAnnouncementIds.clear();
        Mockito.reset(fileStorageService);
    }

    // =========================================================================
    // Upload — happy paths (jpg / png / webp) + key convention
    // =========================================================================

    @Test
    @DisplayName("Upload JPEG to a draft persists a row and a C2.1-convention object key")
    void uploadJpeg_draft_persistsRowWithConventionKey() {
        UUID id = newDraft();
        AnnouncementMediaResponse res = announcementService.uploadMedia(
                id, file("a.jpg", JPEG_BYTES), "inline", UUID.randomUUID());

        assertThat(res.getKind()).isEqualTo(AnnouncementMediaKind.INLINE);
        assertThat(res.getContentType()).isEqualTo("image/jpeg");
        assertThat(res.getObjectKey()).startsWith("announcements/" + id + "/");
        assertThat(mediaRepository.countByAnnouncementId(id)).isEqualTo(1);
        // Stored bytes were uploaded with the Tika-detected type, not the (here matching) client header.
        verify(fileStorageService).upload(Mockito.eq(res.getObjectKey()), Mockito.any(),
                Mockito.eq("image/jpeg"), Mockito.eq((long) JPEG_BYTES.length));
    }

    @Test
    @DisplayName("Upload PNG and WEBP are accepted on a draft")
    void uploadPngAndWebp_accepted() {
        UUID id = newDraft();
        assertThatCode(() -> announcementService.uploadMedia(
                id, file("a.png", PNG_BYTES), "inline", UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> announcementService.uploadMedia(
                id, file("a.webp", WEBP_BYTES), "inline", UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(mediaRepository.countByAnnouncementId(id)).isEqualTo(2);
    }

    // =========================================================================
    // Reject — Tika on bytes, disallowed type, published, bad kind
    // =========================================================================

    @Test
    @DisplayName("Non-image bytes renamed .jpg are rejected by Tika (type not allowed)")
    void uploadTextRenamedJpg_rejected() {
        UUID id = newDraft();
        assertCode(() -> announcementService.uploadMedia(
                        id, file("evil.jpg", TEXT_BYTES), "inline", UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED);
        assertThat(mediaRepository.countByAnnouncementId(id)).isZero();
        verify(fileStorageService, never()).upload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong());
    }

    @Test
    @DisplayName("A real but disallowed type (GIF) is rejected")
    void uploadGif_rejected() {
        UUID id = newDraft();
        assertCode(() -> announcementService.uploadMedia(
                        id, file("a.gif", GIF_BYTES), "inline", UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Upload to a PUBLISHED announcement is rejected (not draft)")
    void uploadToPublished_rejected() {
        UUID id = newPublished();
        assertCode(() -> announcementService.uploadMedia(
                        id, file("a.jpg", JPEG_BYTES), "inline", UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_NOT_DRAFT);
    }

    @Test
    @DisplayName("Invalid kind is rejected (validation error)")
    void uploadInvalidKind_rejected() {
        UUID id = newDraft();
        assertCode(() -> announcementService.uploadMedia(
                        id, file("a.jpg", JPEG_BYTES), "banner", UUID.randomUUID()),
                ErrorCode.VALIDATION_ERROR);
    }

    // =========================================================================
    // Caps — count (5) and total size (50MB), with boundaries
    // =========================================================================

    @Test
    @DisplayName("6th image is rejected; the 5th (boundary) is accepted")
    void countCap_sixthRejected_fifthAccepted() {
        UUID idFull = newDraft();
        for (int i = 0; i < 5; i++) {
            seedMedia(idFull, AnnouncementMediaKind.INLINE, 10L);
        }
        assertCode(() -> announcementService.uploadMedia(
                        idFull, file("a.jpg", JPEG_BYTES), "inline", UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED);

        UUID idFour = newDraft();
        for (int i = 0; i < 4; i++) {
            seedMedia(idFour, AnnouncementMediaKind.INLINE, 10L);
        }
        assertThatCode(() -> announcementService.uploadMedia(
                idFour, file("a.jpg", JPEG_BYTES), "inline", UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(mediaRepository.countByAnnouncementId(idFour)).isEqualTo(5);
    }

    @Test
    @DisplayName("A file pushing total over 50MB is rejected; landing exactly at 50MB is accepted")
    void sizeCap_overRejected_exactAccepted() {
        UUID idOver = newDraft();
        seedMedia(idOver, AnnouncementMediaKind.INLINE, MAX_TOTAL_BYTES);
        assertCode(() -> announcementService.uploadMedia(
                        idOver, file("a.jpg", JPEG_BYTES), "inline", UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED);

        UUID idExact = newDraft();
        seedMedia(idExact, AnnouncementMediaKind.INLINE, MAX_TOTAL_BYTES - JPEG_BYTES.length);
        assertThatCode(() -> announcementService.uploadMedia(
                idExact, file("a.jpg", JPEG_BYTES), "inline", UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(mediaRepository.sumSizeBytesByAnnouncementId(idExact)).isEqualTo(MAX_TOTAL_BYTES);
    }

    // =========================================================================
    // Cover replace — second cover removes the first + after-commit object delete
    // =========================================================================

    @Test
    @DisplayName("Second cover REPLACES the first cover row and schedules the old object for delete")
    void coverReplace_removesOldRowAndDeletesObject() {
        UUID id = newDraft();
        AnnouncementMedia oldCover = seedMedia(id, AnnouncementMediaKind.COVER, 100L);

        AnnouncementMediaResponse res = announcementService.uploadMedia(
                id, file("cover.png", PNG_BYTES), "cover", UUID.randomUUID());

        // Exactly one cover remains, and it is the new row.
        assertThat(mediaRepository.findByAnnouncementIdAndKind(id, AnnouncementMediaKind.COVER))
                .get().extracting(AnnouncementMedia::getId).isEqualTo(res.getId());
        assertThat(mediaRepository.countByAnnouncementId(id)).isEqualTo(1);
        // Old cover object deleted AFTER commit.
        verify(fileStorageService).delete(oldCover.getObjectKey());
    }

    // =========================================================================
    // Delete media — row gone + after-commit object delete; published rejected
    // =========================================================================

    @Test
    @DisplayName("Delete media removes the row and deletes the object after commit")
    void deleteMedia_removesRowAndObject() {
        UUID id = newDraft();
        AnnouncementMedia media = seedMedia(id, AnnouncementMediaKind.INLINE, 10L);

        announcementService.deleteMedia(id, media.getId());

        assertThat(mediaRepository.findByAnnouncementIdAndId(id, media.getId())).isEmpty();
        verify(fileStorageService).delete(media.getObjectKey());
    }

    @Test
    @DisplayName("Delete media on a PUBLISHED announcement is rejected (not draft)")
    void deleteMediaOnPublished_rejected() {
        UUID id = newPublished();
        AnnouncementMedia media = seedMedia(id, AnnouncementMediaKind.INLINE, 10L);
        assertCode(() -> announcementService.deleteMedia(id, media.getId()),
                ErrorCode.ANNOUNCEMENT_NOT_DRAFT);
        // Row untouched, no object deleted.
        assertThat(mediaRepository.findByAnnouncementIdAndId(id, media.getId())).isPresent();
        verify(fileStorageService, never()).delete(Mockito.any());
    }

    // =========================================================================
    // Draft delete — cascades media rows + schedules object cleanup
    // =========================================================================

    @Test
    @DisplayName("Deleting a draft removes its media rows and deletes every object after commit")
    void deleteDraft_cascadesMediaAndObjects() {
        UUID id = newDraft();
        AnnouncementMedia m1 = seedMedia(id, AnnouncementMediaKind.COVER, 10L);
        AnnouncementMedia m2 = seedMedia(id, AnnouncementMediaKind.INLINE, 20L);

        announcementService.deleteAnnouncement(id);
        // Already deleted — drop from cleanup list so @AfterEach does not double-delete.
        createdAnnouncementIds.remove(id);

        assertThat(mediaRepository.findByAnnouncementIdOrderByCreatedAtAsc(id)).isEmpty();
        verify(fileStorageService).delete(m1.getObjectKey());
        verify(fileStorageService).delete(m2.getObjectKey());
        verify(fileStorageService, times(2)).delete(Mockito.any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Asserts the runnable throws an {@link AppException} carrying the expected error code.
     *
     * @param runnable the call expected to fail.
     * @param expected the expected error code.
     */
    private static void assertCode(org.junit.jupiter.api.function.Executable runnable, ErrorCode expected) {
        assertThatThrownBy(runnable::execute)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(expected));
    }

    /**
     * Builds a multipart file part.
     *
     * @param name  the original filename.
     * @param bytes the file content.
     * @return the multipart file.
     */
    private static MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    /**
     * Persists a draft announcement and tracks it for cleanup.
     *
     * @return the new draft id.
     */
    private UUID newDraft() {
        return saveAnnouncement(null);
    }

    /**
     * Persists a published announcement and tracks it for cleanup.
     *
     * @return the new published announcement id.
     */
    private UUID newPublished() {
        return saveAnnouncement(OffsetDateTime.now());
    }

    /**
     * Persists an ALL-scope announcement with the given publish state.
     *
     * @param publishedAt the publish timestamp, or null for a draft.
     * @return the saved announcement id.
     */
    private UUID saveAnnouncement(OffsetDateTime publishedAt) {
        Announcement a = new Announcement();
        a.setTitle("C22-" + System.nanoTime());
        a.setContent("media fixture");
        a.setType(AnnouncementType.GENERAL);
        a.setScope(AnnouncementScope.ALL);
        a.setPublishedAt(publishedAt);
        Announcement saved = announcementRepository.save(a);
        createdAnnouncementIds.add(saved.getId());
        return saved.getId();
    }

    /**
     * Persists a media row directly (for seeding caps / cover-replace without real uploads).
     *
     * @param announcementId the owning announcement id.
     * @param kind           the media kind.
     * @param sizeBytes      the recorded size.
     * @return the saved media row.
     */
    private AnnouncementMedia seedMedia(UUID announcementId, AnnouncementMediaKind kind, long sizeBytes) {
        AnnouncementMedia media = new AnnouncementMedia();
        media.setAnnouncement(announcementRepository.getReferenceById(announcementId));
        media.setObjectKey("announcements/" + announcementId + "/" + UUID.randomUUID() + ".bin");
        media.setContentType("image/jpeg");
        media.setSizeBytes(sizeBytes);
        media.setKind(kind);
        media.setOriginalFilename("seed.bin");
        return mediaRepository.save(media);
    }
}
