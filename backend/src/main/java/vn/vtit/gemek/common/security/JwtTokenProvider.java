/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.vtit.gemek.config.JwtConfig;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Utility component for creating and validating JWT access and refresh tokens.
 *
 * <p>Access tokens: 15-minute expiry, HS256, claims include {@code sub=userId},
 * {@code email}, {@code role}, {@code jti=UUID}.
 *
 * <p>Refresh tokens: 7-day expiry, stored in Redis under key {@code refresh:{userId}:{jti}}.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** Claim key for the user's email address. */
    public static final String CLAIM_EMAIL = "email";

    /** Claim key for the user's role. */
    public static final String CLAIM_ROLE = "role";

    /** Claim key to distinguish token type (access vs refresh). */
    public static final String CLAIM_TOKEN_TYPE = "type";

    /** Token type value for access tokens. */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** Token type value for refresh tokens. */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtConfig jwtConfig;
    private final SecretKey signingKey;

    /**
     * Constructs the provider and derives the HMAC signing key from configuration.
     *
     * @param jwtConfig the JWT configuration properties.
     */
    public JwtTokenProvider(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        // Derive key from the configured secret string using UTF-8 encoding.
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a short-lived access token for the given user principal.
     *
     * @param principal the authenticated user.
     * @return signed JWT access token string.
     */
    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getAccessTokenExpiryMs());

        return Jwts.builder()
                .subject(principal.getId().toString())
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_ROLE, extractRoleName(principal))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a long-lived refresh token for the given user ID.
     *
     * <p>The caller is responsible for storing the JTI in Redis.
     *
     * @param userId the user's UUID.
     * @return signed JWT refresh token string.
     */
    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getRefreshTokenExpiryMs());

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a JWT token, returning its claims.
     *
     * @param token the JWT token string.
     * @return the parsed {@link Claims}.
     * @throws JwtException if the token is invalid, expired, or tampered.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates a JWT token without throwing, returning {@code false} on any error.
     *
     * @param token the JWT token string.
     * @return {@code true} if valid, {@code false} otherwise.
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Extracts the JTI (JWT ID) from a token.
     *
     * @param token the JWT token string.
     * @return the JTI claim value.
     */
    public String extractJti(String token) {
        return parseToken(token).getId();
    }

    /**
     * Extracts the subject (user ID) from a token.
     *
     * @param token the JWT token string.
     * @return the subject claim value as a string UUID.
     */
    public String extractSubject(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Computes the remaining valid lifetime of a token in milliseconds.
     *
     * <p>Used to set the TTL when adding the JTI to the blocklist on logout.
     *
     * @param token the JWT token string.
     * @return remaining milliseconds until expiry; 0 if already expired.
     */
    public long getRemainingExpiryMs(String token) {
        Date expiration = parseToken(token).getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0L);
    }

    /**
     * Extracts the plain role name from a {@link UserPrincipal}'s first authority.
     *
     * <p>Strips the {@code ROLE_} prefix that Spring Security adds.
     *
     * @param principal the user principal.
     * @return the role name without {@code ROLE_} prefix.
     */
    private String extractRoleName(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
    }
}
