/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.config.JwtConfig;

import java.time.Duration;
import vn.vtit.gemek.module.auth.dto.ChangePasswordRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.auth.dto.LoginResponse;
import vn.vtit.gemek.module.auth.dto.RefreshTokenRequest;
import vn.vtit.gemek.module.auth.dto.RefreshTokenResponse;
import vn.vtit.gemek.module.auth.dto.UpdateFcmTokenRequest;
import vn.vtit.gemek.module.auth.dto.UpdateOwnProfileRequest;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Public endpoints: {@code /login}, {@code /refresh}.
 * Protected endpoints: {@code /logout}, {@code /me}, {@code /me/password}, {@code /me/profile},
 * {@code /me/fcm-token}.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication and session management")
public class AuthController {

    /** Bearer token prefix used to extract the raw token from the Authorization header. */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Name of the httpOnly refresh-token cookie (hardening H3). */
    static final String REFRESH_COOKIE_NAME = "refreshToken";

    /** Cookie Path attribute — matches the served controller mapping exactly. */
    static final String AUTH_COOKIE_PATH = "/api/auth";

    /** CSRF belt-and-braces header required on the cookie refresh path only. */
    static final String CSRF_HEADER = "X-Requested-With";

    private final AuthService authService;
    private final JwtConfig jwtConfig;

    /**
     * Secure attribute for the refresh cookie. True in prod (https); false in the
     * http dev/demo deployment — a Secure cookie over http is never sent, which
     * would lock out every login (see application.yml app.auth.cookie-secure).
     */
    @Value("${app.auth.cookie-secure:false}")
    private boolean cookieSecure;

    /**
     * Constructs the controller with its dependencies.
     *
     * @param authService the authentication service.
     * @param jwtConfig   the JWT configuration — cookie Max-Age is read from the same
     *                    refresh-expiry source as the Redis allow-list TTL (no second 7d).
     */
    public AuthController(AuthService authService, JwtConfig jwtConfig) {
        this.authService = authService;
        this.jwtConfig = jwtConfig;
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
        LoginResponse response = authService.login(request, httpRequest);
        // Cookie-only since the hardening close-out: the refresh token is delivered solely
        // as the httpOnly cookie. The service still returns it on the LoginResponse record
        // so we can build the cookie here; @JsonIgnore keeps it off the JSON body.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(response.refreshToken()).toString())
                .body(response);
    }

    /**
     * Exchanges the refresh-token cookie for a new access token.
     *
     * <p>Cookie-only since the hardening close-out (the H3 body-param fallback is gone):
     * the httpOnly {@value #REFRESH_COOKIE_NAME} cookie is the sole accepted source, and it
     * additionally requires the {@value #CSRF_HEADER} header (CSRF belt-and-braces —
     * SameSite=Strict is the first line). A missing cookie is a 401 (no session to refresh);
     * a present cookie without the header is a 403.
     *
     * @param cookieToken the refresh token cookie value, if present.
     * @param csrfHeader  the X-Requested-With header value, if present.
     * @param httpRequest the HTTP request (passed to service for IP-based rate limiting).
     * @return 200 OK with new access token; re-issues the cookie.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Exchange the refresh-token cookie for a new access token.")
    public ResponseEntity<RefreshTokenResponse> refreshToken(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String cookieToken,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest httpRequest) {
        // No cookie → no session to refresh.
        if (!StringUtils.hasText(cookieToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token cookie is required.");
        }
        // Cookie present but the CSRF belt-and-braces header is missing → reject.
        if (!StringUtils.hasText(csrfHeader)) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Header " + CSRF_HEADER + " is required for cookie-based refresh.");
        }
        // SECURITY-FIX: SEC-05 — pass httpRequest for IP-based rate limiting
        RefreshTokenResponse response = authService.refreshToken(new RefreshTokenRequest(cookieToken), httpRequest);
        // Re-issue the cookie (fresh Max-Age; server-side Redis TTL stays authoritative).
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(cookieToken).toString())
                .body(response);
    }

    /**
     * Logs out the authenticated user — blocklists the access token and deletes refresh tokens.
     *
     * @param principal   the authenticated user principal.
     * @param httpRequest the HTTP request (used to extract the raw token).
     * @return 204 No Content; clears the refresh cookie.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate current session tokens.")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        String rawToken = extractBearerToken(httpRequest);
        authService.logout(principal, rawToken);
        // H3: clear the cookie on top of the existing total revocation (untouched).
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
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
     * Updates the authenticated user's own profile (fullName / phone / email).
     *
     * <p>Authenticated-only (any role) via the {@code anyRequest().authenticated()} rule —
     * no {@code @PreAuthorize}. Identity is server-derived from the principal; the body
     * carries no id and cannot change role/isActive/password (privilege-escalation guard).
     *
     * @param principal the authenticated user principal.
     * @param request   the self-editable profile fields.
     * @return 200 OK with the updated profile (same shape as {@code GET /api/auth/me}).
     */
    @PutMapping("/me/profile")
    @Operation(summary = "Update own profile")
    public ResponseEntity<UserDetailResponse> updateOwnProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateOwnProfileRequest request) {
        return ResponseEntity.ok(authService.updateOwnProfile(principal, request));
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
     * Builds the httpOnly refresh cookie (hardening H3, E2=Option 1).
     *
     * <p>Max-Age comes from the SAME config value as the Redis allow-list TTL
     * ({@code jwt.refresh-token-expiry-ms}) — never a second hardcoded duration.
     *
     * @param refreshToken the refresh JWT to store.
     * @return the cookie.
     */
    private ResponseCookie buildRefreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ofMillis(jwtConfig.getRefreshTokenExpiryMs()))
                .build();
    }

    /**
     * Builds the clearing variant of the refresh cookie (Max-Age=0, same attributes).
     *
     * @return the expired cookie.
     */
    private ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(0)
                .build();
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
