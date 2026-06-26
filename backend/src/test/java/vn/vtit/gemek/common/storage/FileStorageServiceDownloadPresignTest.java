/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the C3 forced-download presign overload at the REAL {@link FileStorageService} bean.
 *
 * <p>Presign is offline SigV4 (region pinned), so no MinIO connection is made — this test only inspects
 * the minted URL's query string. It pins that the attachment overload folds
 * {@code response-content-disposition=attachment} + {@code response-content-type=application/octet-stream}
 * into the URL, while the plain image presign carries NEITHER (image URLs stay inline, unchanged).
 */
@SpringBootTest
@ActiveProfiles("test")
class FileStorageServiceDownloadPresignTest extends AbstractIntegrationTest {

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    @DisplayName("Attachment presign overload signs response-content-disposition + octet-stream into the URL")
    void downloadPresign_carriesForcedDownloadParams() {
        String url = fileStorageService.presign(
                "announcements/" + java.util.UUID.randomUUID() + "/" + java.util.UUID.randomUUID() + ".pdf",
                ContentDispositionUtil.attachment("báo cáo.pdf"),
                "application/octet-stream");

        assertThat(url).contains("response-content-disposition=");
        // attachment + octet-stream present (URL-encoded: '/' → %2F).
        assertThat(url).contains("attachment");
        assertThat(url).contains("response-content-type=application%2Foctet-stream");
        // Signed query → the override params are inside the signature's covered set.
        assertThat(url).contains("X-Amz-Signature=");
    }

    @Test
    @DisplayName("Plain (image) presign carries NO Content-Disposition / content-type override — stays inline")
    void plainPresign_hasNoDownloadParams() {
        String url = fileStorageService.presign(
                "announcements/" + java.util.UUID.randomUUID() + "/" + java.util.UUID.randomUUID() + ".png");

        assertThat(url).doesNotContain("response-content-disposition");
        assertThat(url).doesNotContain("response-content-type");
    }
}
