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
     * MinIO server endpoint URL. Example: {@code http://minio:9000}.
     */
    private String endpoint;

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
     * Creates and configures the {@link MinioClient} bean.
     *
     * @return configured {@link MinioClient} instance.
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
