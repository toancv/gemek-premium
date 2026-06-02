/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for CORS policy — GAP-13.
 *
 * <p>Verifies that the security filter chain rejects preflight requests from
 * origins not in the {@code cors.allowed-origins} allowlist, and permits those
 * that are in the list.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // Non-allowlisted origin → preflight rejected (403)
    // =========================================================================

    @Test
    @DisplayName("CORS preflight from non-allowlisted origin is rejected with 403")
    void corsPreflight_evilOrigin_returns403() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Non-allowlisted origin → no ACAO header echoed back
    // =========================================================================

    @Test
    @DisplayName("CORS preflight from non-allowlisted origin does not echo ACAO header")
    void corsPreflight_evilOrigin_doesNotEchoOriginHeader() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // =========================================================================
    // Allowlisted origin → preflight accepted with correct ACAO header
    // =========================================================================

    @Test
    @DisplayName("CORS preflight from allowlisted origin returns 200 with correct ACAO header")
    void corsPreflight_allowedOrigin_returnsOkWithAcaoHeader() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
