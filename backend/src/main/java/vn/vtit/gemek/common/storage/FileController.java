/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for file storage utility endpoints.
 *
 * <p>Any authenticated user may request a presigned URL for a known object key.
 * The caller is responsible for knowing the key (typically stored in their own records).
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "File storage utility endpoints")
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * Constructs the controller with the file storage service dependency.
     *
     * @param fileStorageService the MinIO-backed storage service.
     */
    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Returns a presigned GET URL valid for 1 hour for the given object key.
     *
     * @param key the MinIO object key to generate a URL for.
     * @return 200 OK with a JSON body containing the {@code url} field.
     */
    @GetMapping("/presign")
    @Operation(summary = "Get a presigned download URL for a file object key")
    public ResponseEntity<Map<String, String>> presign(@RequestParam String key) {
        String url = fileStorageService.presign(key);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
