/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.UUID;

/**
 * Default implementation of {@link NotificationService}.
 *
 * <p>All methods run under a read-only transaction by default (class-level annotation).
 * Write methods override this with a full read-write transaction.
 * Constructor injection is used — no {@code @RequiredArgsConstructor}.
 */
@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** Repository for notification persistence and query operations. */
    private final NotificationRepository notificationRepository;

    /** Repository used to validate and load the recipient user. */
    private final UserRepository userRepository;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param notificationRepository the notification JPA repository.
     * @param userRepository         the user JPA repository.
     */
    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads the recipient {@link User} entity before building the record so
     * the foreign-key association is properly set.
     */
    @Override
    @Transactional
    public void createNotification(UUID userId, String title, String body,
                                   NotificationType type, UUID referenceId, String referenceType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "User not found: " + userId));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setReferenceType(referenceType);

        notificationRepository.save(notification);
        log.info("Notification created — userId={}, type={}, title={}", userId, type, title);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<Notification> getMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The repository update is scoped to both the notification ID and the user ID,
     * so a result of 0 rows means either the notification does not exist or it belongs
     * to a different user — both cases surface as NOT_FOUND to the caller.
     */
    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        int updated = notificationRepository.markAsRead(notificationId, userId);
        if (updated == 0) {
            throw new AppException(ErrorCode.NOT_FOUND, "Notification not found: " + notificationId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
        log.info("All notifications marked as read — userId={}", userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long countUnread(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }
}
