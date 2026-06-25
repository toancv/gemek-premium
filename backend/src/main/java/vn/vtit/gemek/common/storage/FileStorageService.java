/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.config.MinioConfig;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Service for storing and retrieving files via MinIO.
 *
 * <p>All object keys are stored in the database (not full URLs). Presigned URLs
 * are generated on demand with a short expiry so the bucket URL can change
 * without requiring any DB migration.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /**
     * Presigned-URL expiry in seconds (hardening H1, P-A). A leaked presigned URL
     * is fetchable by anyone until it expires — keep this window short. Photos are
     * fetched immediately on detail render; clients refetch on focus, so a long
     * window buys nothing. Was 1 hour pre-hardening.
     */
    static final int PRESIGN_EXPIRY_SECONDS = 600;

    private final MinioClient minioClient;
    private final MinioClient minioPresignClient;
    private final MinioConfig minioConfig;

    /**
     * Constructs the service with the internal byte-ops client, the public presign client, and config.
     *
     * @param minioClient        the INTERNAL MinIO client (byte ops — put/delete).
     * @param minioPresignClient the PUBLIC presign client (presigned URLs signed for the browser host).
     * @param minioConfig        the MinIO configuration holding the target bucket name.
     */
    public FileStorageService(@Qualifier("minioClient") MinioClient minioClient,
                              @Qualifier("minioPresignClient") MinioClient minioPresignClient,
                              MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioPresignClient = minioPresignClient;
        this.minioConfig = minioConfig;
    }

    /**
     * Uploads a file to MinIO and returns the stored object key.
     *
     * @param objectKey   the MinIO object key (e.g. {@code tickets/2026/05/uuid.jpg}).
     * @param inputStream the file data stream.
     * @param contentType MIME type string.
     * @param size        byte size of the content.
     * @return the stored object key.
     * @throws AppException with {@code INTERNAL_ERROR} if the upload fails.
     */
    public String upload(String objectKey, InputStream inputStream, String contentType, long size) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            log.debug("MinIO upload succeeded — key={}, size={}", objectKey, size);
            return objectKey;
        } catch (Exception e) {
            log.error("MinIO upload failed for key {}: {}", objectKey, e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "File upload failed.");
        }
    }

    /**
     * Generates a presigned GET URL valid for {@link #PRESIGN_EXPIRY_SECONDS} seconds.
     *
     * @param objectKey the MinIO object key.
     * @return presigned URL string.
     * @throws AppException with {@code INTERNAL_ERROR} if URL generation fails.
     */
    public String presign(String objectKey) {
        try {
            // Sign with the PUBLIC client so the URL host is browser-reachable (offline SigV4).
            return minioPresignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(PRESIGN_EXPIRY_SECONDS, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("MinIO presign failed for key {}: {}", objectKey, e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Could not generate file URL.");
        }
    }

    /**
     * Deletes an object from MinIO.
     *
     * <p>Failures are logged at WARN level but do not throw — deletion is best-effort.
     *
     * @param objectKey the MinIO object key.
     */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .build());
            log.debug("MinIO delete succeeded — key={}", objectKey);
        } catch (Exception e) {
            log.warn("MinIO delete failed for key {}: {}", objectKey, e.getMessage());
        }
    }
}
