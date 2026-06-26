/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import vn.vtit.gemek.module.announcement.dto.AnnouncementAttachmentResponse;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementAttachment;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMedia;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMediaKind;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementAttachmentRepository;
import vn.vtit.gemek.module.announcement.repository.AnnouncementMediaRepository;
import vn.vtit.gemek.module.announcement.repository.AnnouncementRepository;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Service-layer integration coverage for C3 announcement ATTACHMENT upload/list/delete against the real
 * DB and JPQL, with MinIO mocked. Proves the magic-byte allow-list (pdf/docx/xlsx/pptx/txt), the
 * INDEPENDENT caps, draft-only mutation, cascade cleanup including attachments, and the forced-download
 * presign on the detail manifest.
 *
 * <p>Deliberately NOT {@code @Transactional}: the after-commit MinIO cleanup only fires on a real commit.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnnouncementAttachmentServiceIntegrationTest extends AbstractIntegrationTest {

    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /** Minimal valid PDF — %PDF magic makes Tika report application/pdf. */
    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes();

    /** Plain text — Tika reports text/plain (allowed as txt). */
    private static final byte[] TXT_BYTES = "Bien ban hop cu dan thang 6.".getBytes();

    /** HTML — Tika reports text/html (a renderable type → rejected). */
    private static final byte[] HTML_BYTES = "<!DOCTYPE html><html><body><script>1</script></body></html>".getBytes();

    /** SVG — Tika reports image/svg+xml (a script-capable type → rejected). */
    private static final byte[] SVG_BYTES =
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();

    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementAttachmentRepository attachmentRepository;

    @Autowired
    private AnnouncementMediaRepository mediaRepository;

    private final List<UUID> createdAnnouncementIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID id : createdAnnouncementIds) {
            announcementRepository.findById(id).ifPresent(announcementRepository::delete);
        }
        createdAnnouncementIds.clear();
        Mockito.reset(fileStorageService);
    }

    // =========================================================================
    // Type allow-list — accept pdf/docx/xlsx/pptx/txt (magic-byte / zip-peek)
    // =========================================================================

    @Test
    @DisplayName("PDF and TXT are accepted on a draft with the C2.1-convention key")
    void uploadPdfAndTxt_accepted() {
        UUID id = newDraft();
        AnnouncementAttachmentResponse pdf = announcementService.uploadAttachment(
                id, file("báo cáo.pdf", PDF_BYTES), UUID.randomUUID());
        assertThat(pdf.getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getDisplayFilename()).isEqualTo("báo cáo.pdf");

        AnnouncementAttachmentResponse txt = announcementService.uploadAttachment(
                id, file("notes.txt", TXT_BYTES), UUID.randomUUID());
        assertThat(txt.getContentType()).isEqualTo("text/plain");
        assertThat(attachmentRepository.countByAnnouncementId(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("OOXML docx/xlsx/pptx are accepted (zip container disambiguated by part layout)")
    void uploadOoxml_accepted() {
        UUID id = newDraft();
        assertThatCode(() -> announcementService.uploadAttachment(
                id, file("a.docx", ooxml("word/document.xml")), UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> announcementService.uploadAttachment(
                id, file("a.xlsx", ooxml("xl/workbook.xml")), UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> announcementService.uploadAttachment(
                id, file("a.pptx", ooxml("ppt/presentation.xml")), UUID.randomUUID())).doesNotThrowAnyException();

        List<AnnouncementAttachment> rows = attachmentRepository.findByAnnouncementIdOrderByCreatedAtAsc(id);
        assertThat(rows).extracting(AnnouncementAttachment::getContentType).containsExactlyInAnyOrder(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    // =========================================================================
    // Type allow-list — reject html/svg/plain-zip + html-renamed-pdf (not trusting extension)
    // =========================================================================

    @Test
    @DisplayName("HTML is rejected (renderable type) — even renamed .pdf (extension not trusted)")
    void uploadHtml_rejected() {
        UUID id = newDraft();
        assertCode(() -> announcementService.uploadAttachment(
                        id, file("page.html", HTML_BYTES), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED);
        // Same bytes renamed .pdf — content detection still says text/html → rejected.
        assertCode(() -> announcementService.uploadAttachment(
                        id, file("evil.pdf", HTML_BYTES), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED);
        assertThat(attachmentRepository.countByAnnouncementId(id)).isZero();
        verify(fileStorageService, never()).upload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong());
    }

    @Test
    @DisplayName("SVG and a plain (non-OOXML) zip are rejected")
    void uploadSvgAndPlainZip_rejected() {
        UUID id = newDraft();
        assertCode(() -> announcementService.uploadAttachment(
                        id, file("x.svg", SVG_BYTES), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED);
        // Zip with no word/|xl/|ppt/ part tree → application/zip → not allowed.
        assertCode(() -> announcementService.uploadAttachment(
                        id, file("a.zip", ooxml("data/readme.txt")), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED);
    }

    // =========================================================================
    // Draft-only + invalid states
    // =========================================================================

    @Test
    @DisplayName("Upload to a PUBLISHED announcement is rejected (not draft)")
    void uploadToPublished_rejected() {
        UUID id = newPublished();
        assertCode(() -> announcementService.uploadAttachment(
                        id, file("a.pdf", PDF_BYTES), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_NOT_DRAFT);
    }

    @Test
    @DisplayName("Delete attachment on a PUBLISHED announcement is rejected (not draft)")
    void deleteOnPublished_rejected() {
        UUID id = newPublished();
        AnnouncementAttachment a = seedAttachment(id, 10L);
        assertCode(() -> announcementService.deleteAttachment(id, a.getId()),
                ErrorCode.ANNOUNCEMENT_NOT_DRAFT);
        assertThat(attachmentRepository.findByAnnouncementIdAndId(id, a.getId())).isPresent();
        verify(fileStorageService, never()).delete(Mockito.any());
    }

    // =========================================================================
    // Caps — count (5), per-file (10MB), total (50MB), all independent of images
    // =========================================================================

    @Test
    @DisplayName("6th attachment rejected; the 5th (boundary) accepted")
    void countCap_sixthRejected_fifthAccepted() {
        UUID idFull = newDraft();
        for (int i = 0; i < 5; i++) {
            seedAttachment(idFull, 10L);
        }
        assertCode(() -> announcementService.uploadAttachment(
                        idFull, file("a.pdf", PDF_BYTES), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED);

        UUID idFour = newDraft();
        for (int i = 0; i < 4; i++) {
            seedAttachment(idFour, 10L);
        }
        assertThatCode(() -> announcementService.uploadAttachment(
                idFour, file("a.pdf", PDF_BYTES), UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(attachmentRepository.countByAnnouncementId(idFour)).isEqualTo(5);
    }

    @Test
    @DisplayName("A single file over 10MB is rejected (per-file cap)")
    void perFileCap_overRejected() {
        UUID id = newDraft();
        byte[] big = new byte[(int) MAX_FILE_BYTES + 1];
        // Leading %PDF so it would pass type detection if the size check were not first.
        System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);
        assertCode(() -> announcementService.uploadAttachment(
                        id, file("big.pdf", big), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_ATTACHMENT_TOO_LARGE);
        verify(fileStorageService, never()).upload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong());
    }

    @Test
    @DisplayName("A file pushing the per-announcement total over 50MB is rejected")
    void totalCap_overRejected() {
        UUID id = newDraft();
        seedAttachment(id, MAX_TOTAL_BYTES);
        assertCode(() -> announcementService.uploadAttachment(
                        id, file("a.pdf", PDF_BYTES), UUID.randomUUID()),
                ErrorCode.ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Image caps are NOT affected by attachments (5 attachments still allow 5 images)")
    void imageCaps_independentOfAttachments() {
        UUID id = newDraft();
        for (int i = 0; i < 5; i++) {
            seedAttachment(id, 10L);
        }
        // Image path uses its OWN cap (announcement_media), so a 1st image is still accepted.
        assertThatCode(() -> announcementService.uploadMedia(
                id, file("a.png", PNG_BYTES), "inline", UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(mediaRepository.countByAnnouncementId(id)).isEqualTo(1);
        assertThat(attachmentRepository.countByAnnouncementId(id)).isEqualTo(5);
    }

    // =========================================================================
    // Delete + cascade (media AND attachments) + forced-download manifest
    // =========================================================================

    @Test
    @DisplayName("Delete attachment removes the row and deletes the object after commit")
    void deleteAttachment_removesRowAndObject() {
        UUID id = newDraft();
        AnnouncementAttachment a = seedAttachment(id, 10L);
        announcementService.deleteAttachment(id, a.getId());
        assertThat(attachmentRepository.findByAnnouncementIdAndId(id, a.getId())).isEmpty();
        verify(fileStorageService).delete(a.getObjectKey());
    }

    @Test
    @DisplayName("Deleting a draft cascades media AND attachment rows and cleans up both object sets")
    void deleteDraft_cascadesMediaAndAttachments() {
        UUID id = newDraft();
        AnnouncementMedia media = seedMedia(id);
        AnnouncementAttachment att = seedAttachment(id, 20L);

        announcementService.deleteAnnouncement(id);
        createdAnnouncementIds.remove(id);

        assertThat(mediaRepository.findByAnnouncementIdOrderByCreatedAtAsc(id)).isEmpty();
        assertThat(attachmentRepository.findByAnnouncementIdOrderByCreatedAtAsc(id)).isEmpty();
        verify(fileStorageService).delete(media.getObjectKey());
        verify(fileStorageService).delete(att.getObjectKey());
        verify(fileStorageService, times(2)).delete(Mockito.any());
    }

    @Test
    @DisplayName("Detail manifest mints a FORCED-DOWNLOAD presigned URL for ADMIN; image presign stays plain")
    void detailManifest_attachmentForcedDownload() {
        UUID id = newDraft();
        AnnouncementAttachment att = seedAttachment(id, 30L);
        seedMedia(id);
        Mockito.when(fileStorageService.presign(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn("http://localhost:8090/signed-download");
        Mockito.when(fileStorageService.presign(Mockito.any())).thenReturn("http://localhost:8090/signed-inline");

        AnnouncementResponse res = announcementService.getAnnouncement(id, UUID.randomUUID(), "ADMIN");

        assertThat(res.getAttachments()).hasSize(1);
        assertThat(res.getAttachments().get(0).getDisplayFilename()).isEqualTo(att.getDisplayFilename());
        // Forced-download presign called with a Content-Disposition: attachment + octet-stream.
        ArgumentCaptor<String> disp = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).presign(Mockito.eq(att.getObjectKey()), disp.capture(), type.capture());
        assertThat(disp.getValue()).startsWith("attachment;").contains("filename");
        assertThat(type.getValue()).isEqualTo("application/octet-stream");
        // The image row used the PLAIN presign (inline), not the download overload.
        verify(fileStorageService).presign(Mockito.any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** PNG signature stub — Tika detects image/png (for the image-independence test). */
    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n',
            0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'};

    private static void assertCode(org.junit.jupiter.api.function.Executable runnable, ErrorCode expected) {
        assertThatThrownBy(runnable::execute)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(expected));
    }

    private static MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    /**
     * Builds a minimal ZIP carrying a single entry whose path determines the OOXML type classification.
     *
     * @param entryPath the part path (e.g. {@code word/document.xml}); {@code data/...} → a non-OOXML zip.
     * @return the zip bytes.
     */
    private static byte[] ooxml(String entryPath) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<Types/>".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(entryPath));
            zos.write("<x/>".getBytes());
            zos.closeEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID newDraft() {
        return saveAnnouncement(null);
    }

    private UUID newPublished() {
        return saveAnnouncement(OffsetDateTime.now());
    }

    private UUID saveAnnouncement(OffsetDateTime publishedAt) {
        Announcement a = new Announcement();
        a.setTitle("C3-" + System.nanoTime());
        a.setContent("attachment fixture");
        a.setType(AnnouncementType.GENERAL);
        a.setScope(AnnouncementScope.ALL);
        a.setPublishedAt(publishedAt);
        Announcement saved = announcementRepository.save(a);
        createdAnnouncementIds.add(saved.getId());
        return saved.getId();
    }

    /**
     * Persists an attachment row directly (for seeding caps / cascade without real uploads).
     *
     * @param announcementId the owning announcement id.
     * @param sizeBytes      the recorded size.
     * @return the saved attachment row.
     */
    private AnnouncementAttachment seedAttachment(UUID announcementId, long sizeBytes) {
        AnnouncementAttachment a = new AnnouncementAttachment();
        a.setAnnouncement(announcementRepository.getReferenceById(announcementId));
        a.setObjectKey("announcements/" + announcementId + "/" + UUID.randomUUID() + ".pdf");
        a.setContentType("application/pdf");
        a.setSizeBytes(sizeBytes);
        a.setDisplayFilename("seed.pdf");
        return attachmentRepository.save(a);
    }

    /**
     * Persists one image media row directly (for the cascade + image-independence tests).
     *
     * @param announcementId the owning announcement id.
     * @return the saved media row.
     */
    private AnnouncementMedia seedMedia(UUID announcementId) {
        AnnouncementMedia m = new AnnouncementMedia();
        m.setAnnouncement(announcementRepository.getReferenceById(announcementId));
        m.setObjectKey("announcements/" + announcementId + "/" + UUID.randomUUID() + ".png");
        m.setContentType("image/png");
        m.setSizeBytes(10L);
        m.setKind(AnnouncementMediaKind.INLINE);
        m.setOriginalFilename("seed.png");
        return mediaRepository.save(m);
    }
}
