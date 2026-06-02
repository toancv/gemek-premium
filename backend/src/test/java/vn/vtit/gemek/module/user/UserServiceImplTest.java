/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.user.dto.UpdateUserRequest;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.mapper.UserMapper;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserServiceImpl} — SEC-04 role-change audit logging and self-deactivation guard.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder, userMapper);
    }

    // -------------------------------------------------------------------------
    // SEC-04: role change is allowed but must be auditable (WARN log produced)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateUser: role change RESIDENT→ADMIN — persisted with new role (SEC-04 path exercised)")
    void updateUser_roleChange_persistsNewRole() {
        UUID userId = UUID.randomUUID();
        User existing = buildUser(userId, UserRole.RESIDENT);
        UpdateUserRequest request = new UpdateUserRequest("Admin User", "0900000001", UserRole.ADMIN, true);

        // Stub SecurityContext so actorId lookup does not NPE
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin-uuid");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenReturn(existing);

        userService.updateUser(userId, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("updateUser: no role change — save still called, no exception")
    void updateUser_noRoleChange_saveCalledNormally() {
        UUID userId = UUID.randomUUID();
        User existing = buildUser(userId, UserRole.RESIDENT);
        UpdateUserRequest request = new UpdateUserRequest("Same User", "0900000001", UserRole.RESIDENT, true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenReturn(existing);

        userService.updateUser(userId, request);

        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("updateUser: user not found — throws NOT_FOUND")
    void updateUser_userNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = new UpdateUserRequest("Name", null, UserRole.RESIDENT, true);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(userId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // -------------------------------------------------------------------------
    // Self-deactivation guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deactivateUser: self-deactivation — throws SELF_OPERATION_NOT_ALLOWED")
    void deactivateUser_selfDeactivation_throwsSelfOperationNotAllowed() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.deactivateUser(userId, userId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SELF_OPERATION_NOT_ALLOWED));
    }

    @Test
    @DisplayName("deactivateUser: user not found — throws NOT_FOUND")
    void deactivateUser_userNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(userId, actorId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a synthetic {@link User} with the given id and role.
     *
     * @param id   the user UUID.
     * @param role the user role.
     * @return the populated user entity.
     */
    private User buildUser(UUID id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail("test@gemek.vn");
        user.setPasswordHash("$2a$12$hash");
        user.setFullName("Test User");
        user.setPhone("0900000000");
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
