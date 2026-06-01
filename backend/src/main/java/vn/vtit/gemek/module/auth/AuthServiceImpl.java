/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.security.JwtTokenProvider;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.config.JwtConfig;
import vn.vtit.gemek.module.auth.dto.ChangePasswordRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.auth.dto.LoginResponse;
import vn.vtit.gemek.module.auth.dto.RefreshTokenRequest;
import vn.vtit.gemek.module.auth.dto.RefreshTokenResponse;
import vn.vtit.gemek.module.auth.dto.UpdateFcmTokenRequest;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.mapper.UserMapper;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link AuthService}.
 *
 * <p>JWT strategy:
 * <ul>
 *   <li>Access token: 15-min expiry, stored only on client.</li>
 *   <li>Refresh token JTI stored in Redis under key {@code refresh:{userId}:{jti}} with 7-day TTL.</li>
 *   <li>Logout: access token JTI written to {@code blocklist:{jti}} with remaining TTL.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Redis key prefix for refresh tokens. */
    static final String REFRESH_KEY_PREFIX = "refresh:";

    /** Redis key prefix for the JWT blocklist. */
    static final String BLOCKLIST_KEY_PREFIX = "blocklist:";

    /** Redis key prefix for login rate limiting counters. */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate:login:";

    /** Rate limit window in seconds. */
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60L;

    /** Maximum login attempts per IP per 60-second window. Configurable to allow higher limits in tests. */
    private final int loginRateLimit;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtConfig jwtConfig;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    /**
     * Constructs the auth service with its required dependencies.
     *
     * @param userRepository  the user JPA repository.
     * @param passwordEncoder the BCrypt password encoder.
     * @param tokenProvider   the JWT token utility.
     * @param jwtConfig       the JWT configuration properties.
     * @param redisTemplate   the Redis string template.
     * @param userMapper      the MapStruct user mapper.
     */
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           JwtConfig jwtConfig,
                           StringRedisTemplate redisTemplate,
                           UserMapper userMapper,
                           @Value("${auth.rate-limit.max-attempts:10}") int loginRateLimit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtConfig = jwtConfig;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.loginRateLimit = loginRateLimit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        // Rate limit by client IP to prevent brute-force attacks.
        String clientIp = resolveClientIp(httpRequest);
        enforceRateLimit(clientIp);

        // Lookup user by email — invalid email returns the same error as wrong password
        // to prevent user enumeration.
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid email or password."));

        if (!user.isActive()) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE, "Account is deactivated.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }

        // Update last login timestamp.
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        // Store refresh token JTI in Redis for future validation and single-device revocation.
        String refreshJti = tokenProvider.extractJti(refreshToken);
        String refreshKey = REFRESH_KEY_PREFIX + user.getId() + ":" + refreshJti;
        redisTemplate.opsForValue().set(
                refreshKey,
                user.getId().toString(),
                jwtConfig.getRefreshTokenExpiryMs(),
                TimeUnit.MILLISECONDS);

        log.info("User logged in — id={}, role={}", user.getId(), user.getRole());

        long expiresInSeconds = jwtConfig.getAccessTokenExpiryMs() / 1000;
        LoginResponse.UserSummary userSummary = new LoginResponse.UserSummary(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getAvatarUrl());

        return new LoginResponse(accessToken, refreshToken, expiresInSeconds, userSummary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = tokenProvider.parseToken(request.refreshToken());
        } catch (JwtException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Refresh token is invalid or expired.");
        }

        // Verify this is actually a refresh token.
        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE))) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Provided token is not a refresh token.");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String jti = claims.getId();
        String refreshKey = REFRESH_KEY_PREFIX + userId + ":" + jti;

        // Verify the refresh token exists in Redis — it is deleted on logout.
        Boolean exists = redisTemplate.hasKey(refreshKey);
        if (!Boolean.TRUE.equals(exists)) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "Refresh token has been revoked.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN, "User not found."));

        if (!user.isActive()) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE, "Account is deactivated.");
        }

        UserPrincipal principal = new UserPrincipal(user);
        String newAccessToken = tokenProvider.generateAccessToken(principal);
        long expiresInSeconds = jwtConfig.getAccessTokenExpiryMs() / 1000;

        log.debug("Access token refreshed for user id={}", userId);
        return new RefreshTokenResponse(newAccessToken, expiresInSeconds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logout(UserPrincipal principal, String accessToken) {
        // SECURITY-FIX: SEC-13 — guard against null/blank token (e.g. called without Bearer header)
        if (accessToken == null || accessToken.isBlank()) {
            log.debug("Logout called without access token for user id={}", principal.getId());
            return;
        }
        try {
            String jti = tokenProvider.extractJti(accessToken);
            long remainingMs = tokenProvider.getRemainingExpiryMs(accessToken);

            // Add the access token JTI to the blocklist so the filter rejects it.
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(
                        BLOCKLIST_KEY_PREFIX + jti,
                        "1",
                        remainingMs,
                        TimeUnit.MILLISECONDS);
            }

            // Delete all refresh tokens for this user to invalidate all sessions.
            // SECURITY-FIX: SEC-14 — use cursor SCAN instead of blocking O(N) KEYS command
            String pattern = REFRESH_KEY_PREFIX + principal.getId() + ":*";
            List<String> keys = new ArrayList<>();
            try (var cursor = redisTemplate.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(pattern).count(100).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            log.info("User logged out — id={}", principal.getId());
        } catch (JwtException ex) {
            // Token is already invalid — logout is a no-op.
            log.debug("Logout called with invalid token for user id={}", principal.getId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDetailResponse getMe(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        return userMapper.toUserDetailResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Password changed for user id={}", user.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateFcmToken(UserPrincipal principal, UpdateFcmTokenRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        user.setFcmToken(request.fcmToken());
        userRepository.save(user);
        log.debug("FCM token updated for user id={}", user.getId());
    }

    /**
     * Enforces login rate limiting using a Redis counter keyed by client IP.
     *
     * <p>On first request in a window the counter is created with a 60-second TTL.
     * If the counter exceeds the limit, a {@code RATE_LIMITED} exception is thrown.
     *
     * @param clientIp the client IP address.
     * @throws AppException with {@link ErrorCode#RATE_LIMITED} if limit exceeded.
     */
    private void enforceRateLimit(String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);

        // On the first increment, set the expiry for the window.
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count != null && count > loginRateLimit) {
            log.warn("Login rate limit exceeded for IP={}", clientIp);
            throw new AppException(ErrorCode.RATE_LIMITED,
                    "Too many login attempts. Please wait 60 seconds before retrying.");
        }
    }

    /**
     * Resolves the client IP address, honouring the {@code X-Forwarded-For} header
     * only when the immediate connection originates from a private/loopback address
     * (i.e. a trusted reverse proxy). This prevents an attacker from spoofing the
     * header on a direct public connection to bypass rate limiting.
     *
     * @param request the HTTP servlet request.
     * @return the resolved client IP address.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // SECURITY-FIX: only trust X-Forwarded-For when the direct connection is from a
        // private/loopback address (reverse proxy), not from an arbitrary internet client
        if (isPrivateOrLoopback(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                // X-Forwarded-For may contain a comma-separated chain; take the first value.
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    /**
     * Returns {@code true} when the IP is a private or loopback address
     * that identifies a trusted internal reverse proxy.
     *
     * @param ip the IP address string to test.
     * @return whether the address is private or loopback.
     */
    private boolean isPrivateOrLoopback(String ip) {
        if (ip.startsWith("127.") || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return true;
        }
        // RFC 1918: 172.16.0.0/12 covers 172.16.x.x through 172.31.x.x only
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
        return false;
    }
}
