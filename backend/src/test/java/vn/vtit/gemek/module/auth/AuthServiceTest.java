/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.security.JwtTokenProvider;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.config.JwtConfig;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.auth.dto.LoginResponse;
import vn.vtit.gemek.module.auth.dto.RefreshTokenRequest;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.mapper.UserMapper;
import vn.vtit.gemek.module.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl}.
 *
 * <p>Uses Mockito to isolate the service from Redis, JPA, and JWT dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UserMapper userMapper;

    @Mock
    private HttpServletRequest httpRequest;

    private AuthServiceImpl authService;

    private static final String PHONE = "0962464748";

    /** A reusable active admin user for most test scenarios. */
    private User testUser;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, tokenProvider, jwtConfig, redisTemplate, userMapper, 10);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setPhone(PHONE);
        testUser.setEmail("admin@gemek.vn");
        testUser.setFullName("Admin User");
        testUser.setPasswordHash("$2a$12$hashedpassword");
        testUser.setRole(UserRole.ADMIN);
        testUser.setActive(true);

        // Stub Redis value ops used by most test paths.
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    }

    // -------------------------------------------------------------------------
    // login()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("login: valid credentials returns tokens and updates lastLoginAt")
    void login_validCredentials_returnsTokensAndUpdatesLastLoginAt() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Admin@123456", testUser.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access.token.here");
        when(tokenProvider.generateRefreshToken(testUser.getId())).thenReturn("refresh.token.here");
        when(tokenProvider.extractJti("refresh.token.here")).thenReturn("refresh-jti-uuid");
        when(jwtConfig.getAccessTokenExpiryMs()).thenReturn(900_000L);
        when(jwtConfig.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);

        LoginRequest request = new LoginRequest(PHONE, "Admin@123456");
        LoginResponse response = authService.login(request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("access.token.here");
        assertThat(response.refreshToken()).isEqualTo("refresh.token.here");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().phone()).isEqualTo(PHONE);

        // Verify lastLoginAt was updated via save.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLastLoginAt()).isNotNull();

        // Verify refresh token was stored in Redis with correct TTL.
        verify(valueOps).set(
                eq(AuthServiceImpl.REFRESH_KEY_PREFIX + testUser.getId() + ":refresh-jti-uuid"),
                eq(testUser.getId().toString()),
                eq(604_800_000L),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("login: non-canonical +84 format normalized — resolves same account")
    void login_nonCanonicalPhone_normalizedAndResolvesAccount() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        // PhoneUtils.normalize("+84962464748") → "0962464748"; stub on canonical form.
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Admin@123456", testUser.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access.token.here");
        when(tokenProvider.generateRefreshToken(testUser.getId())).thenReturn("refresh.token.here");
        when(tokenProvider.extractJti("refresh.token.here")).thenReturn("refresh-jti-uuid");
        when(jwtConfig.getAccessTokenExpiryMs()).thenReturn(900_000L);
        when(jwtConfig.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);

        LoginRequest request = new LoginRequest("+84962464748", "Admin@123456");
        LoginResponse response = authService.login(request, httpRequest);

        assertThat(response.user().phone()).isEqualTo(PHONE);
    }

    @Test
    @DisplayName("login: wrong password throws INVALID_CREDENTIALS")
    void login_wrongPassword_throwsInvalidCredentials() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpass", testUser.getPasswordHash())).thenReturn(false);

        LoginRequest request = new LoginRequest(PHONE, "wrongpass");

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: unknown phone throws INVALID_CREDENTIALS (no user enumeration)")
    void login_unknownPhone_throwsInvalidCredentials() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(userRepository.findByPhone("0999999999")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("0999999999", "somepass");

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("login: rate limit exceeded throws RATE_LIMITED")
    void login_rateLimitExceeded_throwsRateLimited() {
        // Simulate counter already at 11 (over the 10 req/min limit).
        when(valueOps.increment(anyString())).thenReturn(11L);

        LoginRequest request = new LoginRequest(PHONE, "Admin@123456");

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RATE_LIMITED));
    }

    // -------------------------------------------------------------------------
    // logout()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("logout: adds JTI to blocklist and deletes refresh tokens via SCAN")
    @SuppressWarnings("unchecked")
    void logout_validToken_blocksJtiAndDeletesRefreshTokens() {
        UserPrincipal principal = new UserPrincipal(testUser);
        String accessToken = "valid.access.token";

        // SECURITY-FIX: SEC-14 — logout now uses SCAN cursor instead of KEYS.
        Cursor<String> mockCursor = org.mockito.Mockito.mock(Cursor.class);
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer<String> consumer = inv.getArgument(0);
            consumer.accept("refresh:some-key");
            return null;
        }).when(mockCursor).forEachRemaining(any());

        when(tokenProvider.extractJti(accessToken)).thenReturn("access-jti-uuid");
        when(tokenProvider.getRemainingExpiryMs(accessToken)).thenReturn(300_000L);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(mockCursor);

        authService.logout(principal, accessToken);

        verify(valueOps).set(
                eq(AuthServiceImpl.BLOCKLIST_KEY_PREFIX + "access-jti-uuid"),
                eq("1"),
                eq(300_000L),
                eq(TimeUnit.MILLISECONDS));
        verify(redisTemplate).delete(any(java.util.Collection.class));
    }

    // -------------------------------------------------------------------------
    // refreshToken()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("refreshToken: valid refresh token returns new access token")
    void refreshToken_validToken_returnsNewAccessToken() {
        io.jsonwebtoken.Claims mockClaims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(mockClaims.getSubject()).thenReturn(testUser.getId().toString());
        when(mockClaims.getId()).thenReturn("refresh-jti-uuid");
        when(mockClaims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE)).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);

        when(tokenProvider.parseToken("valid.refresh.token")).thenReturn(mockClaims);
        when(redisTemplate.hasKey(AuthServiceImpl.REFRESH_KEY_PREFIX + testUser.getId() + ":refresh-jti-uuid"))
                .thenReturn(true);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new.access.token");
        when(jwtConfig.getAccessTokenExpiryMs()).thenReturn(900_000L);

        when(valueOps.increment(anyString())).thenReturn(1L);
        RefreshTokenRequest request = new RefreshTokenRequest("valid.refresh.token");
        var response = authService.refreshToken(request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("refreshToken: revoked token (not in Redis) throws INVALID_TOKEN")
    void refreshToken_revokedToken_throwsInvalidToken() {
        io.jsonwebtoken.Claims mockClaims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(mockClaims.getSubject()).thenReturn(testUser.getId().toString());
        when(mockClaims.getId()).thenReturn("revoked-jti");
        when(mockClaims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE)).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);

        when(tokenProvider.parseToken("revoked.refresh.token")).thenReturn(mockClaims);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        when(valueOps.increment(anyString())).thenReturn(1L);
        RefreshTokenRequest request = new RefreshTokenRequest("revoked.refresh.token");

        assertThatThrownBy(() -> authService.refreshToken(request, httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    // -------------------------------------------------------------------------
    // logout() — SEC-13: null/blank token guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("logout: null access token — returns gracefully without throwing (SEC-13)")
    void logout_nullToken_returnsGracefullyWithoutException() {
        UserPrincipal principal = new UserPrincipal(testUser);

        authService.logout(principal, null);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("logout: blank access token — returns gracefully without throwing (SEC-13)")
    void logout_blankToken_returnsGracefullyWithoutException() {
        UserPrincipal principal = new UserPrincipal(testUser);

        authService.logout(principal, "   ");

        verify(redisTemplate, never()).opsForValue();
    }

    // -------------------------------------------------------------------------
    // changePassword() — wrong current password → WRONG_CURRENT_PASSWORD
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("changePassword: wrong current password throws WRONG_CURRENT_PASSWORD")
    void changePassword_wrongCurrentPassword_throwsWrongCurrentPassword() {
        UserPrincipal principal = new UserPrincipal(testUser);
        vn.vtit.gemek.module.auth.dto.ChangePasswordRequest req =
                new vn.vtit.gemek.module.auth.dto.ChangePasswordRequest("wrongPass", "NewPass@1234");

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPass", testUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(principal, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WRONG_CURRENT_PASSWORD));
    }
}
