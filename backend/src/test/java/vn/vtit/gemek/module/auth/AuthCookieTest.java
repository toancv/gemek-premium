/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.auth.dto.RefreshTokenRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hardening H3 — httpOnly refresh-cookie tests, dual-mode (cookie-secure=false context,
 * the dev/demo default; the Secure-present state is asserted in
 * {@link AuthCookieSecureFlagTest} — both states matter, Secure-over-http is the
 * lockout trap).
 *
 * <p>Covers: cookie attributes on login, refresh from cookie alone (with the
 * X-Requested-With belt-and-braces header), cookie path rejected without the header,
 * legacy body path unchanged (no header needed), cookie re-issue on refresh, logout
 * clearing + total revocation regression.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCookieTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Seeded by AdminSeeder. */
    private static final String ADMIN_PHONE = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    @Test
    @DisplayName("login — Set-Cookie: refreshToken with HttpOnly, SameSite=Strict, Path=/api/auth, Max-Age=7d, NO Secure (dev)")
    void login_setsRefreshCookie_withoutSecureInDev() throws Exception {
        MvcResult result = login();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith("refreshToken=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/auth");
        // Max-Age aligned with jwt.refresh-token-expiry-ms (7 days).
        assertThat(setCookie).contains("Max-Age=604800");
        // The lockout trap: Secure MUST be absent when app.auth.cookie-secure=false.
        assertThat(setCookie).doesNotContain("Secure");

        // Dual-mode: the body still carries the refresh token for the pre-H4 FE.
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat((String) body.get("refreshToken")).isNotEmpty();
    }

    @Test
    @DisplayName("refresh — cookie alone + X-Requested-With succeeds and re-issues the cookie")
    void refresh_fromCookieWithHeader_succeeds() throws Exception {
        String refreshToken = loginRefreshToken();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refreshToken=");
        assertThat(setCookie).contains("HttpOnly");
    }

    @Test
    @DisplayName("refresh — cookie WITHOUT X-Requested-With rejected 403")
    void refresh_fromCookieWithoutHeader_isForbidden() throws Exception {
        String refreshToken = loginRefreshToken();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("refresh — legacy body param without any header still succeeds (pre-H4 regression)")
    void refresh_bodyParamWithoutHeader_stillSucceeds() throws Exception {
        String refreshToken = loginRefreshToken();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("refresh — neither cookie nor body token → 400 VALIDATION_ERROR (pre-H3 parity)")
    void refresh_noTokenAnywhere_isValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("logout — clears the cookie (Max-Age=0) and revocation stays total (old refresh rejected)")
    void logout_clearsCookie_andRevocationStaysTotal() throws Exception {
        MvcResult loginResult = login();
        Map<?, ?> body = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String accessToken = (String) body.get("accessToken");
        String refreshToken = (String) body.get("refreshToken");

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = logoutResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refreshToken=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("Path=/api/auth");

        // Existing behavior re-asserted: the old refresh token is revoked server-side,
        // on the cookie path too.
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Logs in as the seeded admin.
     *
     * @return the login MvcResult.
     * @throws Exception on MockMvc failure.
     */
    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
    }

    /**
     * Logs in and extracts the refresh token from the response body.
     *
     * @return the refresh token JWT.
     * @throws Exception on MockMvc failure.
     */
    private String loginRefreshToken() throws Exception {
        Map<?, ?> body = objectMapper.readValue(
                login().getResponse().getContentAsString(), Map.class);
        return (String) body.get("refreshToken");
    }
}
