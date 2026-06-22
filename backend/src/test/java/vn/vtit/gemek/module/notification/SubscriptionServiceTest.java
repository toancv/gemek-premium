/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.module.notification.entity.SubscriptionJoinedVia;
import vn.vtit.gemek.module.notification.repository.NotificationSubscriptionRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SubscriptionServiceImpl} (N3 P2).
 *
 * <p>Covers idempotent subscribe/unsubscribe, participant ID resolution, and
 * the DB-level {@code joined_via} CHECK constraint of V14.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionServiceTest extends AbstractIntegrationTest {

    private static final String ENTITY_TYPE = "Ticket";

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private NotificationSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID userA;
    private UUID userB;
    /** Random per-test entity UUID — table is polymorphic, no FK to satisfy. */
    private UUID entityId;

    @BeforeEach
    void createFixtureUsers() {
        // Phone prefix "05" — unused by other test classes (06/07/08 taken).
        userA = saveUser("0500000001", "Sub Test User A").getId();
        userB = saveUser("0500000002", "Sub Test User B").getId();
        entityId = UUID.randomUUID();
    }

    @Test
    @DisplayName("subscribe creates exactly one row, visible via exists + participants")
    void subscribe_newMembership_createsRow() {
        subscriptionService.subscribe(userA, ENTITY_TYPE, entityId, SubscriptionJoinedVia.CREATOR);

        assertThat(subscriptionRepository
                .existsByUserIdAndEntityTypeAndEntityId(userA, ENTITY_TYPE, entityId)).isTrue();
        assertThat(subscriptionService.participantUserIds(ENTITY_TYPE, entityId))
                .containsExactly(userA);
    }

    @Test
    @DisplayName("double subscribe — still one row, no error, original joinedVia kept")
    void subscribe_calledTwice_remainsSingleRowAndKeepsOriginalJoinedVia() {
        subscriptionService.subscribe(userA, ENTITY_TYPE, entityId, SubscriptionJoinedVia.CREATOR);
        assertThatCode(() -> subscriptionService
                .subscribe(userA, ENTITY_TYPE, entityId, SubscriptionJoinedVia.FOLLOWER))
                .doesNotThrowAnyException();

        assertThat(subscriptionService.participantUserIds(ENTITY_TYPE, entityId))
                .containsExactly(userA);
        // First membership origin wins — the duplicate FOLLOWER attempt is ignored.
        String joinedVia = (String) entityManager.createNativeQuery("""
                        SELECT joined_via FROM notification_subscriptions
                        WHERE user_id = :userId AND entity_type = :entityType AND entity_id = :entityId
                        """)
                .setParameter("userId", userA)
                .setParameter("entityType", ENTITY_TYPE)
                .setParameter("entityId", entityId)
                .getSingleResult();
        assertThat(joinedVia).isEqualTo("CREATOR");
    }

    @Test
    @DisplayName("unsubscribe removes the row")
    void unsubscribe_existingMembership_removesRow() {
        subscriptionService.subscribe(userA, ENTITY_TYPE, entityId, SubscriptionJoinedVia.FOLLOWER);

        subscriptionService.unsubscribe(userA, ENTITY_TYPE, entityId);

        assertThat(subscriptionRepository
                .existsByUserIdAndEntityTypeAndEntityId(userA, ENTITY_TYPE, entityId)).isFalse();
    }

    @Test
    @DisplayName("unsubscribe of a non-existent row is a silent no-op")
    void unsubscribe_noMembership_isNoOp() {
        assertThatCode(() -> subscriptionService.unsubscribe(userA, ENTITY_TYPE, entityId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("participantUserIds returns exactly the subscribed users")
    void participantUserIds_twoSubscribers_returnsExactlyThose() {
        subscriptionService.subscribe(userA, ENTITY_TYPE, entityId, SubscriptionJoinedVia.CREATOR);
        subscriptionService.subscribe(userB, ENTITY_TYPE, entityId, SubscriptionJoinedVia.FOLLOWER);
        // Different entity — must not leak into the queried thread.
        subscriptionService.subscribe(userB, ENTITY_TYPE, UUID.randomUUID(), SubscriptionJoinedVia.FOLLOWER);

        List<UUID> participants = subscriptionService.participantUserIds(ENTITY_TYPE, entityId);

        assertThat(participants).containsExactlyInAnyOrder(userA, userB);
    }

    @Test
    @DisplayName("CHECK constraint rejects an invalid joined_via at DB level")
    void checkConstraint_invalidJoinedVia_rejectedByDatabase() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO notification_subscriptions (user_id, entity_type, entity_id, joined_via)
                        VALUES (:userId, :entityType, :entityId, 'INTRUDER')
                        """)
                .setParameter("userId", userA)
                .setParameter("entityType", ENTITY_TYPE)
                .setParameter("entityId", entityId)
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    /**
     * Persists a minimal active RESIDENT user for subscription fixtures.
     *
     * @param phone    unique normalized phone (test login namespace "05").
     * @param fullName display name.
     * @return the saved user.
     */
    private User saveUser(String phone, String fullName) {
        User user = new User();
        user.setPhone(phone);
        user.setFullName(fullName);
        user.setPasswordHash("test-hash-not-a-real-password");
        user.setRole(UserRole.RESIDENT);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }
}
