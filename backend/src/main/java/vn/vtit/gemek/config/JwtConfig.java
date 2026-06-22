/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties bound from {@code jwt.*} in application.yml.
 *
 * <p>All values are injected from environment variables — no secrets are hardcoded.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /**
     * HMAC-SHA256 signing secret. Must be at least 64 bytes (base64-encoded).
     * Injected from {@code JWT_SECRET} environment variable.
     */
    private String secret;

    /**
     * Access token expiry duration in milliseconds. Default: 900000 (15 minutes).
     */
    private long accessTokenExpiryMs;

    /**
     * Refresh token expiry duration in milliseconds. Default: 604800000 (7 days).
     */
    private long refreshTokenExpiryMs;
}
