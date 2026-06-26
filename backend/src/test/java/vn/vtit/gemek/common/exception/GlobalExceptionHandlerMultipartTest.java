/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the multipart-oversize handler in {@link GlobalExceptionHandler}.
 *
 * <p>The servlet multipart-size limit fires in {@code DispatcherServlet.checkMultipart} BEFORE the
 * controller, so the handler infers the upload surface from the request path to pick a fitting too-large
 * code, and MUST return HTTP 413 (so the frontend's existing 413-branch renders a size message instead of
 * the prior unhandled 500 / mid-stream connection-reset hang). The real end-to-end 413 (Tomcat parsing an
 * over-limit part) is verified at HTTP in the smoke; MockMvc pre-parses parts so it cannot trip the
 * container limit — hence this direct handler unit test.
 */
class GlobalExceptionHandlerMultipartTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MockHttpServletRequest req(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    @DisplayName("Attachment path → 413 with ANNOUNCEMENT_ATTACHMENT_TOO_LARGE")
    void attachmentPath_returns413Coded() {
        ResponseEntity<Map<String, Object>> res = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(10_485_760L),
                req("/api/announcements/abc/attachments"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody()).containsEntry("error", "ANNOUNCEMENT_ATTACHMENT_TOO_LARGE");
    }

    @Test
    @DisplayName("Media path → 413 with ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED")
    void mediaPath_returns413Coded() {
        ResponseEntity<Map<String, Object>> res = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(10_485_760L),
                req("/api/announcements/abc/media"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody()).containsEntry("error", "ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("Other multipart path → 413 with generic PAYLOAD_TOO_LARGE")
    void otherPath_returns413Generic() {
        ResponseEntity<Map<String, Object>> res = handler.handleMaxUploadSize(
                new MultipartException("malformed"),
                req("/api/contracts/abc/attachment"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody()).containsEntry("error", "PAYLOAD_TOO_LARGE");
    }
}
