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
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.ticket.repository.TicketPhotoRepository;

import java.util.Map;

/**
 * REST controller for file storage utility endpoints.
 *
 * <p>Any authenticated user may request a presigned URL for a known object key.
 * The key must correspond to a recorded resource in the database; arbitrary keys
 * are rejected to prevent cross-tenant object enumeration.
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "File storage utility endpoints")
public class FileController {

    private final FileStorageService fileStorageService;
    // SECURITY-FIX: injected to validate that presigned key belongs to a known resource
    private final TicketPhotoRepository ticketPhotoRepository;

    /**
     * Constructs the controller with the file storage service and photo repository dependencies.
     *
     * @param fileStorageService    the MinIO-backed storage service.
     * @param ticketPhotoRepository the ticket photo repository for ownership validation.
     */
    public FileController(FileStorageService fileStorageService,
                          TicketPhotoRepository ticketPhotoRepository) {
        this.fileStorageService = fileStorageService;
        this.ticketPhotoRepository = ticketPhotoRepository;
    }

    /**
     * Returns a presigned GET URL valid for 1 hour for the given object key.
     *
     * <p>The key is validated against known photo records before a URL is issued.
     * Requests for unrecognised keys are rejected with {@code 403 FORBIDDEN}.
     *
     * @param key the MinIO object key to generate a URL for.
     * @return 200 OK with a JSON body containing the {@code url} field.
     */
    @GetMapping("/presign")
    @Operation(summary = "Get a presigned download URL for a file object key")
    public ResponseEntity<Map<String, String>> presign(@RequestParam String key) {
        // SECURITY-FIX: reject arbitrary keys — only issue presigned URLs for recorded photo objects
        if (!ticketPhotoRepository.existsByFileUrl(key)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Access to this resource is not permitted.");
        }
        String url = fileStorageService.presign(key);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
