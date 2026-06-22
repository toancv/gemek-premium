/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
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
 * Hardening — httpOnly refresh-cookie tests, cookie-only after the close-out
 * (cookie-secure=false context, the dev/demo default; the Secure-present state is asserted
 * in {@link AuthCookieSecureFlagTest} — both states matter, Secure-over-http is the
 * lockout trap).
 *
 * <p>Covers: cookie attributes on login, login body carrying NO refresh token, refresh from
 * the cookie (with the X-Requested-With belt-and-braces header), cookie path rejected
 * without the header (403), no cookie rejected (401), cookie re-issue on refresh, logout
 * clearing + total revocation regression.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthCookieTest extends AbstractIntegrationTest {

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

        // Cookie-only: the login JSON body must NOT carry the refresh token.
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.containsKey("refreshToken")).isFalse();
    }

    @Test
    @DisplayName("refresh — cookie + X-Requested-With succeeds, re-issues the cookie, body carries NO refresh token")
    void refresh_fromCookieWithHeader_succeeds() throws Exception {
        String refreshToken = loginRefreshCookie();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // Cookie-only: refresh response body must NOT carry the refresh token.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refreshToken=");
        assertThat(setCookie).contains("HttpOnly");
    }

    @Test
    @DisplayName("refresh — cookie WITHOUT X-Requested-With rejected 403")
    void refresh_fromCookieWithoutHeader_isForbidden() throws Exception {
        String refreshToken = loginRefreshCookie();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("refresh — no cookie → 401 UNAUTHORIZED (no session to refresh; body path removed)")
    void refresh_noCookie_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("refresh — legacy body-param path is gone: body token alone (no cookie) → 401")
    void refresh_bodyParamAlone_isUnauthorized() throws Exception {
        String refreshToken = loginRefreshCookie();

        // The old dual-mode body fallback no longer exists — a JSON body with a refresh
        // token but no cookie must NOT authenticate. No cookie → 401 before the body is read.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken)))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("logout — clears the cookie (Max-Age=0) and revocation stays total (old refresh rejected)")
    void logout_clearsCookie_andRevocationStaysTotal() throws Exception {
        MvcResult loginResult = login();
        Map<?, ?> body = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String accessToken = (String) body.get("accessToken");
        // Cookie-only: the refresh token comes from the Set-Cookie header, not the body.
        String refreshToken = parseRefreshCookie(loginResult.getResponse().getHeader("Set-Cookie"));

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
     * Logs in and extracts the refresh token from the Set-Cookie header.
     *
     * <p>The token is cookie-only — it is no longer in the response body — so tests that need
     * to replay it read it from the cookie the server just set (the realistic browser flow).
     *
     * @return the refresh token JWT.
     * @throws Exception on MockMvc failure.
     */
    private String loginRefreshCookie() throws Exception {
        return parseRefreshCookie(login().getResponse().getHeader("Set-Cookie"));
    }

    /**
     * Parses the refresh-token value out of a {@code Set-Cookie} header string.
     *
     * <p>The controller emits the cookie as a raw header (ResponseCookie), so MockMvc does not
     * expose it via {@code getCookie} — the value must be sliced from the header text. The value
     * sits between {@code refreshToken=} and the first attribute delimiter {@code ;}.
     *
     * @param setCookie the Set-Cookie header value.
     * @return the refresh token JWT.
     */
    private String parseRefreshCookie(String setCookie) {
        int start = "refreshToken=".length();
        int end = setCookie.indexOf(';');
        return setCookie.substring(start, end);
    }
}
