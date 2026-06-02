/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.auth.dto.ChangePasswordRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.auth.dto.LoginResponse;
import vn.vtit.gemek.module.auth.dto.RefreshTokenRequest;
import vn.vtit.gemek.module.auth.dto.RefreshTokenResponse;
import vn.vtit.gemek.module.auth.dto.UpdateFcmTokenRequest;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Public endpoints: {@code /login}, {@code /refresh}.
 * Protected endpoints: {@code /logout}, {@code /me}, {@code /me/password}, {@code /me/fcm-token}.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication and session management")
public class AuthController {

    /** Bearer token prefix used to extract the raw token from the Authorization header. */
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    /**
     * Constructs the controller with the auth service dependency.
     *
     * @param authService the authentication service.
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * <p>Rate limited to 10 requests/min per IP (enforced in service layer via Redis).
     *
     * @param request     the login credentials.
     * @param httpRequest the HTTP request (passed to service for IP-based rate limiting).
     * @return 200 OK with access token, refresh token, and user summary.
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password. Returns JWT tokens.")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    /**
     * Exchanges a valid refresh token for a new access token.
     *
     * @param request the refresh token request body.
     * @return 200 OK with new access token.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Exchange a valid refresh token for a new access token.")
    public ResponseEntity<RefreshTokenResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        // SECURITY-FIX: SEC-05 — pass httpRequest for IP-based rate limiting
        return ResponseEntity.ok(authService.refreshToken(request, httpRequest));
    }

    /**
     * Logs out the authenticated user — blocklists the access token and deletes refresh tokens.
     *
     * @param principal   the authenticated user principal.
     * @param httpRequest the HTTP request (used to extract the raw token).
     * @return 204 No Content.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate current session tokens.")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        String rawToken = extractBearerToken(httpRequest);
        authService.logout(principal, rawToken);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the authenticated user's own profile.
     *
     * @param principal the authenticated user principal.
     * @return 200 OK with user detail.
     */
    @GetMapping("/me")
    @Operation(summary = "Get own profile")
    public ResponseEntity<UserDetailResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getMe(principal));
    }

    /**
     * Changes the authenticated user's own password.
     *
     * @param principal the authenticated user principal.
     * @param request   the change password request body.
     * @return 204 No Content.
     */
    @PutMapping("/me/password")
    @Operation(summary = "Change own password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the authenticated user's FCM device token.
     *
     * @param principal the authenticated user principal.
     * @param request   the FCM token update request body.
     * @return 204 No Content.
     */
    @PutMapping("/me/fcm-token")
    @Operation(summary = "Update FCM device token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateFcmTokenRequest request) {
        authService.updateFcmToken(principal, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     *
     * @param request the HTTP servlet request.
     * @return the raw JWT string, or an empty string if absent.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        // SECURITY-FIX: SEC-13 — return null so logout() can detect missing token
        return null;
    }
}
