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
import vn.vtit.gemek.common.util.PhoneUtils;
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
 *
 * <p>Phone is normalized via {@link PhoneUtils#normalize(String)} before storage so that
 * non-canonical env values (e.g. {@code +84900000000}) produce the canonical form
 * required by the {@code uq_users_phone} UNIQUE constraint (V12 migration).
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminPhone;

    /**
     * Constructs the seeder with all dependencies and config values injected.
     *
     * @param userRepository  repository for user persistence.
     * @param passwordEncoder BCrypt encoder (strength 12) from SecurityConfig.
     * @param adminEmail      admin email, bound from {@code ADMIN_EMAIL} env var.
     * @param adminPassword   plaintext admin password, bound from {@code ADMIN_PASSWORD} env var.
     * @param adminPhone      admin phone (any accepted VN format), bound from {@code ADMIN_PHONE} env var;
     *                        defaults to {@code 0900000000}.
     */
    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.email:admin@gemek.vn}") String adminEmail,
                       @Value("${app.admin.password:}") String adminPassword,
                       @Value("${app.admin.phone:0900000000}") String adminPhone) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminPhone = adminPhone;
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

        // Normalize phone — throws VALIDATION_ERROR (surfaces as startup failure) if misconfigured.
        String normalizedPhone = PhoneUtils.normalize(adminPhone);

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("System Administrator");
        admin.setPhone(normalizedPhone);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);

        userRepository.save(admin);
        log.info("Admin user seeded — phone={}", normalizedPhone);
    }
}
