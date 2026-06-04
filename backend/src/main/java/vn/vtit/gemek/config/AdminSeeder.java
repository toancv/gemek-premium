/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

/**
 * Seeds the default admin user on application startup.
 *
 * <p>Idempotent: skips seeding if any ADMIN-role user already exists.
 * Fail-loud: throws {@link IllegalStateException} if no admin exists and
 * {@code ADMIN_PASSWORD} is blank, preventing silent deployments with no admin access.
 *
 * <p>Password is hashed in-JVM using the configured {@link PasswordEncoder} (BCrypt-12).
 * The plaintext password is never logged.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    /**
     * Constructs the seeder with all dependencies and config values injected.
     *
     * @param userRepository  repository for user persistence.
     * @param passwordEncoder BCrypt encoder (strength 12) from SecurityConfig.
     * @param adminEmail      admin email, bound from {@code ADMIN_EMAIL} env var.
     * @param adminPassword   plaintext admin password, bound from {@code ADMIN_PASSWORD} env var.
     */
    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.email:admin@gemek.vn}") String adminEmail,
                       @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    /**
     * Seeds the admin user if none exists.
     *
     * @param args application arguments (unused).
     * @throws IllegalStateException if no admin exists and {@code ADMIN_PASSWORD} is blank.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.info("Admin already present, skipping seed.");
            return;
        }

        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException(
                    "No admin user exists and ADMIN_PASSWORD is blank. " +
                    "Set the ADMIN_PASSWORD environment variable before starting the application.");
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("System Administrator");
        admin.setPhone("0900000000");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);

        userRepository.save(admin);
        log.info("Admin user seeded for email: {}", adminEmail);
    }
}
