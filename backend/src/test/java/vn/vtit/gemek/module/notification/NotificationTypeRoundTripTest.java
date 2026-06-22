/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip persistence tests for the {@link NotificationType} values added in V13 (N3 P1).
 *
 * <p>Proves the {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)} mapping accepts the new
 * PostgreSQL {@code notification_type} values: each value is INSERTed, the persistence
 * context is cleared to force a real SELECT, and the read-back value is asserted.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationTypeRoundTripTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    /** Seeded admin account from V2 — reused as the notification recipient. */
    private static final String ADMIN_EMAIL = "admin@gemek.vn";

    /**
     * Persists a notification with each new V13 enum value and reads it back from the DB.
     *
     * @param type the N3 notification type under test.
     */
    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {
            "TICKET_CREATED",
            "TICKET_SLA_WARNING",
            "HOUSEHOLD_MEMBER_ADDED",
            "TICKET_RATING_REQUESTED"
    })
    @DisplayName("New notification_type values survive a DB round-trip")
    void newEnumValue_persistAndReload_roundTripsThroughNamedEnumColumn(NotificationType type) {
        User admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Seeded admin user not found"));

        Notification notification = new Notification();
        notification.setUser(admin);
        notification.setTitle("Round-trip " + type.name());
        notification.setBody("V13 enum value round-trip check.");
        notification.setType(type);
        notification.setReferenceId(UUID.randomUUID());
        notification.setReferenceType("Ticket");

        UUID id = notificationRepository.saveAndFlush(notification).getId();

        // Clear the persistence context so the reload below issues a real SELECT.
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(id)
                .orElseThrow(() -> new AssertionError("Notification not found after flush: " + id));
        assertThat(reloaded.getType()).isEqualTo(type);
    }
}
