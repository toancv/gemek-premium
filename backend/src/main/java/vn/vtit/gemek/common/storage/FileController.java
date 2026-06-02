/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.ticket.TicketService;

import java.util.Map;

/**
 * REST controller for file storage utility endpoints.
 *
 * <p>Presigned URLs are issued only after verifying the caller has read access to the ticket
 * that owns the photo — applying the same role-based gate as the ticket detail endpoint.
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "File storage utility endpoints")
public class FileController {

    private final FileStorageService fileStorageService;
    private final TicketService ticketService;

    /**
     * Constructs the controller with its required dependencies.
     *
     * @param fileStorageService the MinIO-backed storage service.
     * @param ticketService      the ticket service used for ownership validation.
     */
    public FileController(FileStorageService fileStorageService, TicketService ticketService) {
        this.fileStorageService = fileStorageService;
        this.ticketService = ticketService;
    }

    /**
     * Returns a presigned GET URL valid for 1 hour for the given object key.
     *
     * <p>The caller must have read access to the ticket that owns the photo.
     * RESIDENT may only access their own apartment's photos; TECHNICIAN must be assigned
     * or the ticket must be NEW; ADMIN and BOARD_MEMBER are unrestricted.
     *
     * @param key       the MinIO object key to generate a URL for.
     * @param principal the authenticated caller.
     * @return 200 OK with a JSON body containing the {@code url} field.
     */
    @GetMapping("/presign")
    @Operation(summary = "Get a presigned download URL for a file object key")
    public ResponseEntity<Map<String, String>> presign(
            @RequestParam String key,
            @AuthenticationPrincipal UserPrincipal principal) {
        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
        // SEC-01: verify ownership before issuing presigned URL
        ticketService.assertPresignAccess(key, principal.getId(), role);
        String url = fileStorageService.presign(key);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
