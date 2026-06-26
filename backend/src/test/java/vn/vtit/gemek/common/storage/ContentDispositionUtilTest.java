/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ContentDispositionUtil} — the RFC 6266/5987 forced-download header builder.
 *
 * <p>The header value is signed into the presigned-URL query param, so a filename must never be able
 * to inject CR/LF/quote content or break the quoted-string. These tests pin that guarantee for quotes,
 * CRLF/control chars, and Vietnamese (non-ASCII) names.
 */
class ContentDispositionUtilTest {

    @Test
    @DisplayName("Plain ASCII filename: quoted fallback + percent-encoded filename*")
    void plainAscii() {
        String value = ContentDispositionUtil.attachment("report.pdf");
        assertThat(value).isEqualTo("attachment; filename=\"report.pdf\"; filename*=UTF-8''report.pdf");
    }

    @Test
    @DisplayName("Quotes are stripped from the ASCII fallback and percent-encoded in filename*")
    void quotesNeutralised() {
        String value = ContentDispositionUtil.attachment("a\"b.pdf");
        // No raw quote survives inside the quoted-string fallback (would break the header).
        assertThat(value).startsWith("attachment; filename=\"ab.pdf\";");
        // The quote (0x22) is percent-encoded in the ext-value.
        assertThat(value).contains("filename*=UTF-8''a%22b.pdf");
    }

    @Test
    @DisplayName("CRLF / control chars cannot inject header content")
    void crlfNeutralised() {
        String value = ContentDispositionUtil.attachment("evil\r\nSet-Cookie: x.txt");
        // No CR or LF anywhere in the produced value.
        assertThat(value).doesNotContain("\r").doesNotContain("\n");
        // CR (%0D) and LF (%0A) are percent-encoded in filename*.
        assertThat(value).contains("%0D%0A");
    }

    @Test
    @DisplayName("Vietnamese name: '_' ASCII fallback + UTF-8 percent-encoding in filename*")
    void vietnameseName() {
        String value = ContentDispositionUtil.attachment("Báo_cáo.pdf");
        // Non-ASCII chars become '_' in the legacy fallback.
        assertThat(value).contains("filename=\"B_o_c_o.pdf\"");
        // 'á' = U+00E1 = UTF-8 0xC3 0xA1 → %C3%A1; the ASCII tail (_cáo.pdf) encodes the second 'á' too.
        assertThat(value).contains("filename*=UTF-8''B%C3%A1o_c%C3%A1o.pdf");
    }

    @Test
    @DisplayName("Null / blank filename falls back to 'download'")
    void nullFilename() {
        assertThat(ContentDispositionUtil.attachment(null))
                .isEqualTo("attachment; filename=\"download\"; filename*=UTF-8''download");
        assertThat(ContentDispositionUtil.attachment("   "))
                .contains("filename*=UTF-8''download");
    }
}
