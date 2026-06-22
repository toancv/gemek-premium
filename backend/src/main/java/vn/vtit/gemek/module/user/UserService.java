/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.dto.ResetPasswordRequest;
import vn.vtit.gemek.module.user.dto.UpdateUserRequest;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;
import vn.vtit.gemek.module.user.dto.UserResponse;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.UUID;

/**
 * Service interface for user management operations.
 *
 * <p>All mutating operations are ADMIN-only; enforcement is done at the
 * controller layer via {@code @PreAuthorize}.
 */
public interface UserService {

    /**
     * Lists users with optional filters.
     *
     * @param role     optional role filter.
     * @param isActive optional active-status filter.
     * @param search   optional substring search on name or email.
     * @param pageable pagination and sort parameters.
     * @return paginated list of user summaries.
     */
    PageResponse<UserResponse> listUsers(UserRole role, Boolean isActive, String search, Pageable pageable);

    /**
     * Creates a new user account.
     *
     * @param request the create request containing email, name, role, and password.
     * @return the created user summary.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code EMAIL_ALREADY_EXISTS} if email is taken.
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param id the user's UUID.
     * @return the detailed user response.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code NOT_FOUND} if not found.
     */
    UserDetailResponse getUserById(UUID id);

    /**
     * Updates a user's profile fields.
     *
     * @param id      the user's UUID.
     * @param request the update request.
     * @return the updated user summary.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code NOT_FOUND} if not found.
     */
    UserResponse updateUser(UUID id, UpdateUserRequest request);

    /**
     * Soft-deletes a user by setting {@code is_active = false}.
     *
     * <p>Cannot deactivate the caller's own account.
     *
     * @param id            the target user's UUID.
     * @param requestUserId the UUID of the admin making the request.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code NOT_FOUND} if target not found.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code SELF_OPERATION_NOT_ALLOWED} if self-delete.
     */
    void deactivateUser(UUID id, UUID requestUserId);

    /**
     * Resets a user's password (admin operation).
     *
     * @param id      the target user's UUID.
     * @param request the new password request.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code NOT_FOUND} if target not found.
     */
    void resetPassword(UUID id, ResetPasswordRequest request);
}
