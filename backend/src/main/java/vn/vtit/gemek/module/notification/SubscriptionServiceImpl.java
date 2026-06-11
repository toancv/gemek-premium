/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.module.notification.entity.SubscriptionJoinedVia;
import vn.vtit.gemek.module.notification.repository.NotificationSubscriptionRepository;

import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link SubscriptionService}.
 *
 * <p>Subscribe uses an exists-check plus a native {@code ON CONFLICT DO NOTHING}
 * insert: the exists-check skips the write on the common already-subscribed
 * path, and the conflict-ignoring insert absorbs the remaining race window
 * without raising — a unique violation caught in Java inside a transaction
 * would still mark it rollback-only, so the DB-side ignore is the only
 * variant that cannot fail a surrounding dispatch transaction.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    /** Repository for subscription persistence and participant queries. */
    private final NotificationSubscriptionRepository subscriptionRepository;

    /**
     * Constructs the service with its required dependency.
     *
     * @param subscriptionRepository the subscription JPA repository.
     */
    public SubscriptionServiceImpl(NotificationSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void subscribe(UUID userId, String entityType, UUID entityId,
                          SubscriptionJoinedVia joinedVia) {
        // Fast path: already a participant — keep the original joinedVia.
        if (subscriptionRepository.existsByUserIdAndEntityTypeAndEntityId(userId, entityType, entityId)) {
            return;
        }
        int inserted = subscriptionRepository.insertIfAbsent(
                userId, entityType, entityId, joinedVia.name());
        log.debug("subscribe — userId={}, entity={}/{}, joinedVia={}, inserted={}",
                userId, entityType, entityId, joinedVia, inserted);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void unsubscribe(UUID userId, String entityType, UUID entityId) {
        long deleted = subscriptionRepository
                .deleteByUserIdAndEntityTypeAndEntityId(userId, entityType, entityId);
        log.debug("unsubscribe — userId={}, entity={}/{}, deleted={}",
                userId, entityType, entityId, deleted);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UUID> participantUserIds(String entityType, UUID entityId) {
        return subscriptionRepository.findParticipantUserIds(entityType, entityId);
    }
}
