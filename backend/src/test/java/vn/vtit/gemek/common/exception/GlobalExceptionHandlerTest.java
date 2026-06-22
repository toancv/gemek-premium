/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Covers the DataIntegrityViolationException → 409 mapping added as defense-in-depth
 * against DB unique-constraint violations that bypass the service-layer guard (e.g. race conditions).
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/residents");
    }

    // -------------------------------------------------------------------------
    // DataIntegrityViolationException — unique constraint → 409 CONFLICT
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DataIntegrityViolationException — returns 409 CONFLICT, not 500")
    void handleDataIntegrityViolation_uniqueConstraint_returns409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uq_users_phone\"");

        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", ErrorCode.CONFLICT.name());
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody()).containsEntry("path", "/api/residents");
    }

    @Test
    @DisplayName("DataIntegrityViolationException — error code is CONFLICT (not INTERNAL_ERROR)")
    void handleDataIntegrityViolation_errorCodeIsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("constraint violation");

        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getBody().get("error")).isEqualTo("CONFLICT");
    }
}
