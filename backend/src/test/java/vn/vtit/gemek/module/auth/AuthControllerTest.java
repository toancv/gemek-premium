/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AuthController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>Uses {@code @SpringBootTest} with full application context to test the complete
 * request pipeline including security filter, JWT filter, and service layer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Seeded by AdminSeeder: phone 0900000000, email admin@gemek.vn, password GemekAdmin2026. */
    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_EMAIL    = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/login — valid credentials returns 200 with tokens")
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // Cookie-only since the hardening close-out: no refresh token in the JSON body.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.phone").value(ADMIN_PHONE))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/auth/login — wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest(ADMIN_PHONE, "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/auth/login — missing phone returns 400 validation error")
    void login_missingPhone_returns400() throws Exception {
        Map<String, String> body = Map.of("password", "somepass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/refresh
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/refresh — valid refresh token returns new access token")
    void refresh_validToken_returnsNewAccessToken() throws Exception {
        // First login to get a refresh token.
        LoginRequest loginRequest = new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // Cookie-only: the refresh token is delivered as the httpOnly cookie, not the body.
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        String refreshToken = setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));

        // Exchange the cookie (with the required CSRF header) for a new access token.
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/logout + GET /api/auth/me
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/logout — returns 204 and subsequent request returns 401")
    void logout_validToken_returns204AndSubsequentRequestReturns401() throws Exception {
        // Login to get tokens.
        LoginRequest loginRequest = new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> loginResponse = objectMapper.readValue(responseBody, Map.class);
        String accessToken = (String) loginResponse.get("accessToken");

        // Logout.
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Attempt to use the same token — must be rejected.
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me — returns user profile for authenticated user")
    void getMe_authenticated_returnsProfile() throws Exception {
        // Login.
        LoginRequest loginRequest = new LoginRequest(ADMIN_PHONE, ADMIN_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class);
        String accessToken = (String) loginResponse.get("accessToken");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.isActive").value(true));
    }
}
