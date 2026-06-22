/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.ticket.TicketService;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FileController} — SEC-01 presign ownership regression guards.
 *
 * <p>Verifies that the presign endpoint delegates ownership checks to {@link TicketService}
 * and correctly propagates FORBIDDEN/NOT_FOUND exceptions raised by it.
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private FileController fileController;

    // -------------------------------------------------------------------------
    // SEC-01 regression: non-owner RESIDENT must not receive a presigned URL
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("presign: non-owner RESIDENT — assertPresignAccess throws FORBIDDEN")
    void presign_nonOwnerResident_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = buildPrincipal(userId, UserRole.RESIDENT);
        String key = "tickets/photo-abc.jpg";

        doThrow(new AppException(ErrorCode.FORBIDDEN, "Access denied to this ticket."))
                .when(ticketService).assertPresignAccess(key, userId, "RESIDENT");

        assertThatThrownBy(() -> fileController.presign(key, principal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(ticketService).assertPresignAccess(key, userId, "RESIDENT");
    }

    @Test
    @DisplayName("presign: ADMIN caller — ownership check passes, presigned URL returned")
    void presign_adminCaller_returnsPresignedUrl() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = buildPrincipal(userId, UserRole.ADMIN);
        String key = "tickets/photo-abc.jpg";
        String expectedUrl = "https://minio.example.com/presigned?sig=xyz";

        doNothing().when(ticketService).assertPresignAccess(key, userId, "ADMIN");
        when(fileStorageService.presign(key)).thenReturn(expectedUrl);

        var response = fileController.presign(key, principal);

        assertThat(response.getBody()).containsEntry("url", expectedUrl);
        verify(ticketService).assertPresignAccess(key, userId, "ADMIN");
    }

    @Test
    @DisplayName("presign: key has no photo record — NOT_FOUND propagated")
    void presign_photoKeyNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = buildPrincipal(userId, UserRole.RESIDENT);
        String key = "tickets/nonexistent-key.jpg";

        doThrow(new AppException(ErrorCode.NOT_FOUND, "No photo record found for the requested key."))
                .when(ticketService).assertPresignAccess(key, userId, "RESIDENT");

        assertThatThrownBy(() -> fileController.presign(key, principal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link UserPrincipal} from a synthetic {@link User} with the given id and role.
     *
     * @param id   the user UUID.
     * @param role the user role.
     * @return the constructed principal.
     */
    private UserPrincipal buildPrincipal(UUID id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail("test@gemek.vn");
        user.setPasswordHash("$2a$12$hash");
        user.setRole(role);
        user.setActive(true);
        return new UserPrincipal(user);
    }
}
