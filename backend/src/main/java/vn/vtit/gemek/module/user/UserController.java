/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.dto.ResetPasswordRequest;
import vn.vtit.gemek.module.user.dto.UpdateUserRequest;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;
import vn.vtit.gemek.module.user.dto.UserResponse;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.UUID;

/**
 * REST controller for user management endpoints.
 *
 * <p>All endpoints in this controller require the {@code ADMIN} role.
 * Role enforcement is applied at the method level via {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User account management (ADMIN only)")
public class UserController {

    private final UserService userService;

    /**
     * Constructs the controller with the user service dependency.
     *
     * @param userService the user service.
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists users with optional filters. Default sort is {@code createdAt desc}.
     *
     * @param role      optional role filter.
     * @param isActive  optional active-status filter.
     * @param search    optional substring search on full name or email.
     * @param page      0-based page index (default 0).
     * @param size      page size (default 20, max 100).
     * @param sort      sort field (default createdAt).
     * @param direction sort direction (default desc).
     * @return paginated list of user summaries.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users", description = "Paginated user list with optional role/status/search filters.")
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        // Cap page size at 100 to prevent unbounded queries.
        int cappedSize = Math.min(size, 100);
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, cappedSize, Sort.by(sortDir, sort));

        return ResponseEntity.ok(userService.listUsers(role, isActive, search, pageable));
    }

    /**
     * Creates a new user account.
     *
     * @param request the create user request body.
     * @return 201 Created with the new user summary.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create user")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    /**
     * Retrieves a user by ID.
     *
     * @param id the user UUID path variable.
     * @return 200 OK with user detail.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserDetailResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Updates a user's profile.
     *
     * @param id      the user UUID path variable.
     * @param request the update request body.
     * @return 200 OK with updated user summary.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * Soft-deletes (deactivates) a user. Cannot deactivate own account.
     *
     * @param id        the target user UUID.
     * @param principal the authenticated admin making the request.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate user")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.deactivateUser(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Resets another user's password (admin force-reset).
     *
     * @param id      the target user UUID.
     * @param request the new password request body.
     * @return 204 No Content.
     */
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset user password (admin)")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
