/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.vtit.gemek.config.MinioConfig;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Option B (dual MinioClient) — proves a presigned GET URL is signed for the PUBLIC endpoint host,
 * while the internal client (byte ops) stays on the internal endpoint. Presign is offline SigV4, so
 * no MinIO container is needed — a client pointed at a fake public host generates a URL without
 * connecting. Pure unit test (no Spring context).
 */
class PresignPublicHostTest {

    /**
     * Builds a {@link MinioConfig} with distinct internal vs public endpoints and dummy credentials.
     *
     * @param publicEndpoint the public endpoint (may be blank to exercise the fallback).
     * @return the configured holder.
     */
    private MinioConfig config(String publicEndpoint) {
        MinioConfig cfg = new MinioConfig();
        cfg.setEndpoint("http://minio:9000");
        cfg.setPublicEndpoint(publicEndpoint);
        cfg.setAccessKey("test-access");
        cfg.setSecretKey("test-secret-key-1234");
        cfg.setBucket("gemek");
        return cfg;
    }

    @Test
    @DisplayName("presign() signs the URL for the PUBLIC endpoint host:port, not the internal one")
    void presign_usesPublicHost() {
        MinioConfig cfg = config("http://localhost:8090");
        FileStorageService svc = new FileStorageService(
                cfg.minioClient(), cfg.minioPresignClient(), cfg);

        URI url = URI.create(svc.presign("announcements/" + "a/b.png"));

        // Signed for the browser-reachable public host (so SignedHeaders=host matches the browser).
        assertThat(url.getHost()).isEqualTo("localhost");
        assertThat(url.getPort()).isEqualTo(8090);
        // Internal docker host never leaks into the browser-facing URL.
        assertThat(url.getHost()).isNotEqualTo("minio");
        assertThat(url.getQuery()).contains("X-Amz-Signature").contains("X-Amz-SignedHeaders=host");
    }

    @Test
    @DisplayName("presign() falls back to the internal endpoint when public-endpoint is unset")
    void presign_fallsBackToInternalWhenUnset() {
        MinioConfig cfg = config("");
        FileStorageService svc = new FileStorageService(
                cfg.minioClient(), cfg.minioPresignClient(), cfg);

        URI url = URI.create(svc.presign("announcements/a/b.png"));

        // No public endpoint configured -> unchanged behaviour (signed for the internal host).
        assertThat(url.getHost()).isEqualTo("minio");
        assertThat(url.getPort()).isEqualTo(9000);
    }
}
