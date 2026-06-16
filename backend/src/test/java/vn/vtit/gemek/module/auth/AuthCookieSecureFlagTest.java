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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hardening H3 — second context: {@code app.auth.cookie-secure=true} (the prod state)
 * plus a low rate-limit override to prove SEC-05 applies on the cookie refresh path.
 *
 * <p>Rate-limit isolation: the limiter keys on client IP and honours
 * {@code X-Forwarded-For}, and Redis is shared across test contexts in one run —
 * every request here uses a synthetic per-test IP so cross-class counters can
 * never interfere (in either direction).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.auth.cookie-secure=true", "auth.rate-limit.max-attempts=3"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCookieSecureFlagTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Seeded by AdminSeeder. */
    private static final String ADMIN_PHONE = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    @Test
    @DisplayName("login with cookie-secure=true — Set-Cookie carries the Secure attribute")
    void login_secureTrue_cookieHasSecureAttribute() throws Exception {
        MvcResult result = login(syntheticIp());

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refreshToken=");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
    }

    @Test
    @DisplayName("SEC-05 regression — cookie refresh path is rate limited (limit 3 → 4th request 429)")
    void refresh_cookiePath_isRateLimited() throws Exception {
        // Cookie-only: the refresh token comes from the Set-Cookie header, not the body.
        String setCookie = login(syntheticIp()).getResponse().getHeader("Set-Cookie");
        String refreshToken = setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));

        String refreshIp = syntheticIp();
        // Requests 1–3 within the limit succeed on the cookie path.
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                            .header("X-Requested-With", "XMLHttpRequest")
                            .header("X-Forwarded-For", refreshIp))
                    .andExpect(status().isOk());
        }
        // Request 4 exceeds auth.rate-limit.max-attempts=3.
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("X-Forwarded-For", refreshIp))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Logs in as the seeded admin from the given synthetic client IP.
     *
     * @param ip the X-Forwarded-For value isolating this call's rate budget.
     * @return the login MvcResult.
     * @throws Exception on MockMvc failure.
     */
    private MvcResult login(String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", ip)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
    }

    /**
     * Generates a unique synthetic private IP per call.
     *
     * @return an IP in 10.x.y.z derived from nanoTime.
     */
    private String syntheticIp() {
        long tag = System.nanoTime();
        return "10." + (tag % 250 + 1) + "." + (tag / 250 % 250 + 1) + "." + (tag / 62500 % 250 + 1);
    }
}
