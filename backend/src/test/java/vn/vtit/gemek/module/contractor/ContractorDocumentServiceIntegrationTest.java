/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;
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
import vn.vtit.gemek.module.contractor.dto.ContractorDocumentResponse;
import vn.vtit.gemek.module.contractor.entity.Contractor;
import vn.vtit.gemek.module.contractor.entity.ContractorDocument;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.contractor.repository.ContractorDocumentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Service-layer integration coverage for contractor DOCUMENT upload/list/delete against the real DB,
 * with MinIO mocked. Proves the magic-byte allow-list (pdf/docx/xlsx/pptx/txt), the per-contractor
 * caps, the after-commit object cleanup on delete, the staff-only presign gate (incl. malformed-key
 * denial), and the forced-download presign on the list manifest.
 *
 * <p>Deliberately NOT {@code @Transactional}: the after-commit MinIO cleanup only fires on a real commit.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContractorDocumentServiceIntegrationTest extends AbstractIntegrationTest {

    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /** Minimal valid PDF — %PDF magic makes Tika report application/pdf. */
    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes();

    /** Plain text — Tika reports text/plain (allowed as txt). */
    private static final byte[] TXT_BYTES = "Hop dong dich vu thang 6.".getBytes();

    /** HTML — Tika reports text/html (a renderable type → rejected). */
    private static final byte[] HTML_BYTES = "<!DOCTYPE html><html><body><script>1</script></body></html>".getBytes();

    /** SVG — Tika reports image/svg+xml (a script-capable type → rejected). */
    private static final byte[] SVG_BYTES =
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();

    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private ContractorService contractorService;

    @Autowired
    private ContractorRepository contractorRepository;

    @Autowired
    private ContractorDocumentRepository documentRepository;

    private final List<UUID> createdContractorIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID id : createdContractorIds) {
            contractorRepository.findById(id).ifPresent(contractorRepository::delete);
        }
        createdContractorIds.clear();
        Mockito.reset(fileStorageService);
    }

    // =========================================================================
    // Type allow-list
    // =========================================================================

    @Test
    @DisplayName("PDF and TXT are accepted with the contractors/{id}/documents key convention")
    void uploadPdfAndTxt_accepted() {
        UUID id = newContractor();
        ContractorDocumentResponse pdf = contractorService.uploadDocument(
                id, file("hợp đồng.pdf", PDF_BYTES), UUID.randomUUID());
        assertThat(pdf.getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getDisplayFilename()).isEqualTo("hợp đồng.pdf");
        assertThat(pdf.getDownloadUrl()).isNull(); // upload response carries no URL

        contractorService.uploadDocument(id, file("notes.txt", TXT_BYTES), UUID.randomUUID());
        assertThat(documentRepository.countByContractorId(id)).isEqualTo(2);
        // Object key shape: contractors/{id}/documents/{uuid}{ext}
        assertThat(documentRepository.findByContractorIdOrderByCreatedAtAsc(id))
                .allSatisfy(d -> assertThat(d.getObjectKey())
                        .startsWith("contractors/" + id + "/documents/"));
    }

    @Test
    @DisplayName("OOXML docx/xlsx/pptx are accepted (zip container disambiguated by part layout)")
    void uploadOoxml_accepted() {
        UUID id = newContractor();
        assertThatCode(() -> contractorService.uploadDocument(
                id, file("a.docx", ooxml("word/document.xml")), UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> contractorService.uploadDocument(
                id, file("a.xlsx", ooxml("xl/workbook.xml")), UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> contractorService.uploadDocument(
                id, file("a.pptx", ooxml("ppt/presentation.xml")), UUID.randomUUID())).doesNotThrowAnyException();

        assertThat(documentRepository.findByContractorIdOrderByCreatedAtAsc(id))
                .extracting(ContractorDocument::getContentType).containsExactlyInAnyOrder(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    @Test
    @DisplayName("HTML/SVG/plain-zip are rejected — even renamed .pdf (extension not trusted), no row")
    void uploadDisallowed_rejected() {
        UUID id = newContractor();
        assertCode(() -> contractorService.uploadDocument(id, file("page.html", HTML_BYTES), UUID.randomUUID()),
                ErrorCode.CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED);
        // Same bytes renamed .pdf — content detection still says text/html → rejected.
        assertCode(() -> contractorService.uploadDocument(id, file("evil.pdf", HTML_BYTES), UUID.randomUUID()),
                ErrorCode.CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED);
        assertCode(() -> contractorService.uploadDocument(id, file("x.svg", SVG_BYTES), UUID.randomUUID()),
                ErrorCode.CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED);
        // Zip with no word/|xl/|ppt/ part tree → application/zip → not allowed.
        assertCode(() -> contractorService.uploadDocument(id, file("a.zip", ooxml("data/readme.txt")), UUID.randomUUID()),
                ErrorCode.CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED);
        assertThat(documentRepository.countByContractorId(id)).isZero();
        verify(fileStorageService, never()).upload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong());
    }

    // =========================================================================
    // Caps — count (5), per-file (10MB), total (50MB), per contractor
    // =========================================================================

    @Test
    @DisplayName("6th document rejected; the 5th (boundary) accepted")
    void countCap_sixthRejected_fifthAccepted() {
        UUID idFull = newContractor();
        for (int i = 0; i < 5; i++) {
            seedDocument(idFull, 10L);
        }
        assertCode(() -> contractorService.uploadDocument(idFull, file("a.pdf", PDF_BYTES), UUID.randomUUID()),
                ErrorCode.CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED);

        UUID idFour = newContractor();
        for (int i = 0; i < 4; i++) {
            seedDocument(idFour, 10L);
        }
        assertThatCode(() -> contractorService.uploadDocument(
                idFour, file("a.pdf", PDF_BYTES), UUID.randomUUID())).doesNotThrowAnyException();
        assertThat(documentRepository.countByContractorId(idFour)).isEqualTo(5);
    }

    @Test
    @DisplayName("A single file over 10MB is rejected (per-file cap), no upload")
    void perFileCap_overRejected() {
        UUID id = newContractor();
        byte[] big = new byte[(int) MAX_FILE_BYTES + 1];
        System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);
        assertCode(() -> contractorService.uploadDocument(id, file("big.pdf", big), UUID.randomUUID()),
                ErrorCode.CONTRACTOR_DOCUMENT_TOO_LARGE);
        verify(fileStorageService, never()).upload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong());
    }

    @Test
    @DisplayName("A file pushing the per-contractor total over 50MB is rejected")
    void totalCap_overRejected() {
        UUID id = newContractor();
        seedDocument(id, MAX_TOTAL_BYTES);
        assertCode(() -> contractorService.uploadDocument(id, file("a.pdf", PDF_BYTES), UUID.randomUUID()),
                ErrorCode.CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED);
    }

    // =========================================================================
    // Delete + cleanup
    // =========================================================================

    @Test
    @DisplayName("Delete document removes the row and deletes the object after commit")
    void deleteDocument_removesRowAndObject() {
        UUID id = newContractor();
        ContractorDocument d = seedDocument(id, 10L);
        contractorService.deleteDocument(id, d.getId());
        assertThat(documentRepository.findByContractorIdAndId(id, d.getId())).isEmpty();
        verify(fileStorageService).delete(d.getObjectKey());
    }

    @Test
    @DisplayName("Delete a document not owned by the contractor in the path → NOT_FOUND (dual-key)")
    void deleteDocument_crossContractor_notFound() {
        UUID idA = newContractor();
        UUID idB = newContractor();
        ContractorDocument dB = seedDocument(idB, 10L);
        assertCode(() -> contractorService.deleteDocument(idA, dB.getId()), ErrorCode.NOT_FOUND);
        assertThat(documentRepository.findByContractorIdAndId(idB, dB.getId())).isPresent();
    }

    // =========================================================================
    // List manifest — forced-download presign minted for staff; empty for non-staff
    // =========================================================================

    @Test
    @DisplayName("List mints a FORCED-DOWNLOAD presigned URL for ADMIN")
    void list_adminForcedDownload() {
        UUID id = newContractor();
        ContractorDocument d = seedDocument(id, 30L);
        Mockito.when(fileStorageService.presign(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn("https://minio.local/signed-download");

        List<ContractorDocumentResponse> docs = contractorService.listDocuments(id, UUID.randomUUID(), "ADMIN");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getDownloadUrl()).isEqualTo("https://minio.local/signed-download");
        ArgumentCaptor<String> disp = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).presign(Mockito.eq(d.getObjectKey()), disp.capture(), type.capture());
        assertThat(disp.getValue()).startsWith("attachment;").contains("filename");
        assertThat(type.getValue()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("List for a non-staff role returns an empty list and never mints a URL")
    void list_nonStaff_empty() {
        UUID id = newContractor();
        seedDocument(id, 10L);
        assertThat(contractorService.listDocuments(id, UUID.randomUUID(), "TECHNICIAN")).isEmpty();
        assertThat(contractorService.listDocuments(id, UUID.randomUUID(), "RESIDENT")).isEmpty();
        verify(fileStorageService, never()).presign(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // =========================================================================
    // Presign gate — staff-only, malformed key denied
    // =========================================================================

    @Test
    @DisplayName("Presign gate: ADMIN/BOARD allowed; TECHNICIAN/RESIDENT denied; malformed key denied")
    void presignGate_staffOnly_andMalformedDenied() {
        String key = "contractors/" + UUID.randomUUID() + "/documents/" + UUID.randomUUID() + ".pdf";
        assertThatCode(() -> contractorService.assertContractorDocumentPresignAccess(key, UUID.randomUUID(), "ADMIN"))
                .doesNotThrowAnyException();
        assertThatCode(() -> contractorService.assertContractorDocumentPresignAccess(key, UUID.randomUUID(), "BOARD_MEMBER"))
                .doesNotThrowAnyException();
        assertCode(() -> contractorService.assertContractorDocumentPresignAccess(key, UUID.randomUUID(), "TECHNICIAN"),
                ErrorCode.FORBIDDEN);
        assertCode(() -> contractorService.assertContractorDocumentPresignAccess(key, UUID.randomUUID(), "RESIDENT"),
                ErrorCode.FORBIDDEN);
        // Malformed keys → denied for every role (never a 500).
        assertCode(() -> contractorService.assertContractorDocumentPresignAccess(
                "contractors/not-a-uuid/documents/x.pdf", UUID.randomUUID(), "ADMIN"), ErrorCode.FORBIDDEN);
        assertCode(() -> contractorService.assertContractorDocumentPresignAccess(
                "announcements/" + UUID.randomUUID() + "/x.pdf", UUID.randomUUID(), "ADMIN"), ErrorCode.FORBIDDEN);
        assertCode(() -> contractorService.assertContractorDocumentPresignAccess(
                "contractors/" + UUID.randomUUID() + "/documents/", UUID.randomUUID(), "ADMIN"), ErrorCode.FORBIDDEN);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private UUID newContractor() {
        Contractor c = new Contractor();
        c.setCompanyName("DocCo-" + System.nanoTime());
        c.setSpecialty(ContractorSpecialty.OTHER);
        Contractor saved = contractorRepository.save(c);
        createdContractorIds.add(saved.getId());
        return saved.getId();
    }

    /**
     * Persists a document row directly (for seeding caps / delete without real uploads).
     *
     * @param contractorId the owning contractor id.
     * @param sizeBytes    the recorded size.
     * @return the saved document row.
     */
    private ContractorDocument seedDocument(UUID contractorId, long sizeBytes) {
        ContractorDocument d = new ContractorDocument();
        d.setContractor(contractorRepository.getReferenceById(contractorId));
        d.setObjectKey("contractors/" + contractorId + "/documents/" + UUID.randomUUID() + ".pdf");
        d.setContentType("application/pdf");
        d.setSizeBytes(sizeBytes);
        d.setDisplayFilename("seed.pdf");
        return documentRepository.save(d);
    }
}
