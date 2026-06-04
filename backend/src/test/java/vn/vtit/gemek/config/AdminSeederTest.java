/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminSeeder}.
 *
 * <p>Verifies idempotent seeding, blank-password fail-loud, and correct
 * hash storage without exercising the real BCrypt or database.
 */
@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "TestAdmin@2026";

    /**
     * Seeds an admin user when none exists and ADMIN_PASSWORD is set.
     * Verifies role, email, active flag, and that the hash comes from the encoder.
     */
    @Test
    @DisplayName("seeds admin when none exists and password is configured")
    void seedsAdminWhenNoneExistsAndPasswordSet() throws Exception {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn("bcrypt-hashed");

        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD);
        seeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hashed");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getFullName()).isNotBlank();
    }

    /**
     * Does nothing when an admin already exists — no save, no password change.
     */
    @Test
    @DisplayName("does nothing when admin already exists")
    void doesNothingWhenAdminAlreadyExists() throws Exception {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD);
        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    /**
     * Fails loud when ADMIN_PASSWORD is blank and no admin exists.
     * Prevents silent deployment with no admin access.
     */
    @Test
    @DisplayName("throws IllegalStateException when password is blank and no admin exists")
    void throwsWhenPasswordBlankAndNoAdmin() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, "");

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }
}
