/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.audit.AuditableEntity;
import vn.vtit.gemek.common.audit.CreatableEntity;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Spring Data JPA auditing actor capture (AUD.1).
 *
 * <p>Covers: actor populated from the authenticated principal on persist/update;
 * null actor (no NPE) when the SecurityContext is empty (seed/scheduler/Flyway path);
 * append-only entities capture {@code createdBy} only and carry no {@code updatedBy}; and
 * the pre-existing manual timestamps remain populated (no regression).
 *
 * <p>Runs against the shared dev DB, so every assertion targets only rows this test
 * created and the test is {@code @Transactional} (rolled back per method).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class AuditingActorCaptureIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** Unique-phone source to avoid collisions on the shared dev DB. */
    private static final AtomicLong PHONE_SEQ = new AtomicLong(System.nanoTime());

    /** First actor — creator. */
    private User actorOne;

    /** Second actor — modifier, to prove updatedBy tracks the modifier. */
    private User actorTwo;

    @BeforeEach
    void setUp() {
        // Actors are created with no authentication on purpose — their own createdBy is null.
        SecurityContextHolder.clearContext();
        actorOne = userRepository.saveAndFlush(newUser("Actor One"));
        actorTwo = userRepository.saveAndFlush(newUser("Actor Two"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Mutable entity captures createdBy on persist and updatedBy on update from the principal")
    void mutableEntity_capturesActorOnPersistAndUpdate() {
        authenticateAs(actorOne);
        User subject = userRepository.saveAndFlush(newUser("Subject"));

        // On insert Spring Data sets both creator and modifier to the current actor.
        assertThat(subject.getCreatedBy()).isEqualTo(actorOne.getId());
        assertThat(subject.getUpdatedBy()).isEqualTo(actorOne.getId());

        // Detach so the update is a real reload-modify-flush cycle under a different actor.
        entityManager.flush();
        entityManager.clear();

        authenticateAs(actorTwo);
        User reloaded = userRepository.findById(subject.getId()).orElseThrow();
        reloaded.setFullName("Subject Renamed");
        userRepository.saveAndFlush(reloaded);

        // createdBy is immutable (set once); updatedBy tracks the modifier.
        assertThat(reloaded.getCreatedBy()).isEqualTo(actorOne.getId());
        assertThat(reloaded.getUpdatedBy()).isEqualTo(actorTwo.getId());
    }

    @Test
    @DisplayName("Null actor: persist with empty SecurityContext leaves createdBy null and throws nothing")
    void nullActor_leavesCreatedByNull_noException() {
        SecurityContextHolder.clearContext();

        // The save itself must not raise (covers seed/scheduler/Flyway with no principal).
        User subject = userRepository.saveAndFlush(newUser("System Created"));

        assertThat(subject.getCreatedBy()).as("no actor -> createdBy null").isNull();
        assertThat(subject.getUpdatedBy()).as("no actor -> updatedBy null").isNull();
    }

    @Test
    @DisplayName("Append-only entity captures createdBy and has no updatedBy field/column")
    void appendOnlyEntity_capturesCreatedBy_andHasNoUpdatedBy() {
        authenticateAs(actorOne);

        Notification notification = new Notification();
        notification.setUser(actorOne);
        notification.setTitle("Test notification");
        notification.setType(NotificationType.TICKET_CREATED);
        Notification saved = notificationRepository.saveAndFlush(notification);

        assertThat(saved.getCreatedBy()).isEqualTo(actorOne.getId());

        // The append-only base must not expose an updatedBy field anywhere in the hierarchy.
        assertThat(hasField(Notification.class, "updatedBy"))
                .as("append-only Notification must not have updatedBy").isFalse();
        assertThat(hasField(CreatableEntity.class, "updatedBy")).isFalse();
        assertThat(hasField(CreatableEntity.class, "createdBy")).isTrue();
        // Sanity: the mutable base does carry updatedBy.
        assertThat(hasField(AuditableEntity.class, "updatedBy")).isTrue();
    }

    @Test
    @DisplayName("Regression: manual created_at/updated_at timestamps still populated alongside auditing")
    void timestamps_remainPopulated() {
        authenticateAs(actorOne);
        User subject = userRepository.saveAndFlush(newUser("Timestamped"));

        assertThat(subject.getCreatedAt()).as("created_at still set by @PrePersist").isNotNull();
        assertThat(subject.getUpdatedAt()).as("updated_at still set by @PrePersist").isNotNull();
    }

    /**
     * Builds a minimally-valid {@link User} with a unique phone and no email.
     *
     * @param fullName the display name.
     * @return an unsaved user.
     */
    private User newUser(String fullName) {
        User user = new User();
        user.setFullName(fullName);
        // Unique numeric phone (<= 20 chars) to survive the shared-DB unique constraint.
        user.setPhone(String.format("%013d", PHONE_SEQ.incrementAndGet() % 1_000_000_000_000L));
        user.setPasswordHash("$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttest");
        user.setRole(UserRole.ADMIN);
        user.setActive(true);
        return user;
    }

    /**
     * Sets the SecurityContext to an authenticated {@link UserPrincipal} for the given user.
     *
     * @param user the user to authenticate as.
     */
    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * Reflectively reports whether a declared field of the given name exists on the type
     * or any superclass.
     *
     * @param type the class to inspect.
     * @param name the field name.
     * @return {@code true} if the field exists anywhere in the hierarchy.
     */
    private static boolean hasField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                c.getDeclaredField(name);
                return true;
            } catch (NoSuchFieldException ignored) {
                // Keep walking up the hierarchy.
            }
        }
        return false;
    }
}
