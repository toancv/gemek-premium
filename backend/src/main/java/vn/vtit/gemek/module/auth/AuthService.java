/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;

import jakarta.servlet.http.HttpServletRequest;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.auth.dto.ChangePasswordRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.auth.dto.LoginResponse;
import vn.vtit.gemek.module.auth.dto.RefreshTokenRequest;
import vn.vtit.gemek.module.auth.dto.RefreshTokenResponse;
import vn.vtit.gemek.module.auth.dto.UpdateFcmTokenRequest;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;

/**
 * Service interface for authentication and session management operations.
 */
public interface AuthService {

    /**
     * Authenticates a user with email and password.
     *
     * <p>On success: updates {@code last_login_at}, issues access and refresh tokens,
     * stores the refresh token JTI in Redis.
     *
     * @param request     the login credentials.
     * @param httpRequest the HTTP request (used for rate limiting by IP).
     * @return login response containing tokens and user summary.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code INVALID_CREDENTIALS} on failure.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code RATE_LIMITED} when limit exceeded.
     */
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Exchanges a valid refresh token for a new access token.
     *
     * <p>Rate limited by client IP to prevent token stuffing and DoS (SEC-05).
     *
     * @param request     the refresh token request.
     * @param httpRequest the HTTP request used for IP-based rate limiting.
     * @return new access token response.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code INVALID_TOKEN} if expired or not found in Redis.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code RATE_LIMITED} when limit exceeded.
     */
    RefreshTokenResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest);

    /**
     * Invalidates the current session by blocklisting the access token JTI
     * and deleting the refresh token from Redis.
     *
     * @param principal   the authenticated user.
     * @param accessToken the raw access token string from the Authorization header.
     */
    void logout(UserPrincipal principal, String accessToken);

    /**
     * Returns the authenticated user's own profile.
     *
     * @param principal the authenticated user.
     * @return detailed user response.
     */
    UserDetailResponse getMe(UserPrincipal principal);

    /**
     * Changes the authenticated user's own password.
     *
     * @param principal the authenticated user.
     * @param request   the change password request.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code INVALID_CREDENTIALS} if current password is wrong.
     */
    void changePassword(UserPrincipal principal, ChangePasswordRequest request);

    /**
     * Updates the authenticated user's FCM device token.
     *
     * @param principal the authenticated user.
     * @param request   the FCM token update request.
     */
    void updateFcmToken(UserPrincipal principal, UpdateFcmTokenRequest request);
}
