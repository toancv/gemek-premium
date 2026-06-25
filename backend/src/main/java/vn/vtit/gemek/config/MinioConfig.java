/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import io.minio.MinioClient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * MinIO client configuration bound from {@code minio.*} in application.yml.
 *
 * <p>All credentials are injected from environment variables — no secrets are hardcoded.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /**
     * MinIO server endpoint URL for SDK byte operations (put/delete). This is the INTERNAL
     * docker-network address the backend container connects to. Example: {@code http://minio:9000}.
     */
    private String endpoint;

    /**
     * Public MinIO endpoint that presigned GET URLs are SIGNED for — the host the browser actually
     * reaches (MinIO fronted by nginx; dev {@code http://localhost:8090}, prod object subdomain). The
     * presign is offline SigV4, so this client never connects; only the signed host matters, and the
     * signature binds {@code SignedHeaders=host} so the browser host must equal this. Defaults to
     * {@link #endpoint} when unset (then presign behaves exactly as before).
     */
    private String publicEndpoint;

    /**
     * MinIO access key (equivalent to AWS_ACCESS_KEY_ID).
     */
    private String accessKey;

    /**
     * MinIO secret key (equivalent to AWS_SECRET_ACCESS_KEY).
     */
    private String secretKey;

    /**
     * Target bucket for all application objects.
     */
    private String bucket;

    /**
     * S3 region the clients are pinned to. Set EXPLICITLY so the SDK never issues a GetBucketLocation
     * network call when signing a presigned URL — without it, presign connects to the endpoint to
     * resolve the region, which FAILS for the public presign client (its host is unreachable from the
     * backend container). MinIO's default region is {@code us-east-1}.
     */
    private String region = "us-east-1";

    /**
     * Creates the INTERNAL {@link MinioClient} used for byte operations (put/delete). Built against
     * {@link #endpoint} (the docker-network address). Marked {@link Primary} so it is the default
     * {@code MinioClient} for any other injection point.
     *
     * @return the internal MinIO client.
     */
    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Creates the PRESIGN-ONLY {@link MinioClient} used to generate presigned GET URLs. Built against
     * {@link #publicEndpoint} (browser-reachable host) so the SigV4 signature binds the public host.
     * Falls back to {@link #endpoint} when {@code publicEndpoint} is unset — then presign is unchanged.
     * Presign is offline crypto, so this client never opens a connection to the public host.
     *
     * @return the public presign MinIO client.
     */
    @Bean
    public MinioClient minioPresignClient() {
        // Default to the internal endpoint when no public endpoint is configured (no behaviour change).
        String presignEndpoint = (publicEndpoint != null && !publicEndpoint.isBlank())
                ? publicEndpoint
                : endpoint;
        return MinioClient.builder()
                .endpoint(presignEndpoint)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
    }
}
