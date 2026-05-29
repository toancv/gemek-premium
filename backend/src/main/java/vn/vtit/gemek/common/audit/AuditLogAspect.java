/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.vtit.gemek.common.audit.entity.AuditLog;
import vn.vtit.gemek.common.audit.repository.AuditLogRepository;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.UUID;

/**
 * AOP aspect that intercepts {@link Auditable}-annotated service methods and
 * writes an {@link AuditLog} record to the database.
 *
 * <p>The audit save is wrapped in a try/catch so a persistence failure never
 * rolls back or interrupts the primary business transaction. This fire-and-forget
 * safety ensures auditing is best-effort and non-blocking.
 *
 * <p>The authenticated user is resolved from the Spring Security context.
 * {@code null} is stored when the call originates from a scheduler or
 * unauthenticated context.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    /** Repository used to persist audit log records. */
    private final AuditLogRepository auditLogRepository;

    /** Repository used to load the {@link User} entity for the FK association. */
    private final UserRepository userRepository;

    /**
     * Constructs the aspect with its required dependencies.
     *
     * @param auditLogRepository the audit log JPA repository.
     * @param userRepository     the user JPA repository.
     */
    public AuditLogAspect(AuditLogRepository auditLogRepository,
                          UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /**
     * Intercepts all {@link Auditable}-annotated service methods.
     *
     * <p>Proceeds with the method invocation first, then saves an {@link AuditLog}
     * record in a fire-and-forget try/catch block so audit failures are never
     * propagated to the caller.
     *
     * @param joinPoint the intercepted method join point.
     * @param auditable the {@link Auditable} annotation instance.
     * @return the result of the intercepted method.
     * @throws Throwable if the intercepted method throws.
     */
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        log.debug("Audit intercept — action={}, entityType={}, method={}",
                auditable.action(), auditable.entityType(), joinPoint.getSignature().toShortString());

        // Proceed with the primary business logic first.
        Object result = joinPoint.proceed();

        // Fire-and-forget: audit failure must never break the primary transaction.
        try {
            UUID actorId = resolveActorId();
            User actor = null;
            if (actorId != null) {
                // Use orElse(null) — missing user should not fail the audit write.
                actor = userRepository.findById(actorId).orElse(null);
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setUser(actor);
            auditLog.setAction(auditable.action());
            auditLog.setEntityType(auditable.entityType());
            // Entity ID extraction from return value is best-effort; callers that
            // need precise entity IDs should supply them via the annotation or a
            // dedicated service method. For now, try to cast result to a type
            // exposing getId(); fall back to null gracefully.
            auditLog.setEntityId(resolveEntityId(result));

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved — action={}, entityType={}, actorId={}",
                    auditable.action(), auditable.entityType(), actorId);
        } catch (Exception auditException) {
            // Log but never rethrow — audit must not break the caller.
            log.warn("Audit log save failed — action={}, entityType={}, reason={}",
                    auditable.action(), auditable.entityType(), auditException.getMessage());
        }

        return result;
    }

    /**
     * Resolves the UUID of the currently authenticated user from the Spring Security context.
     *
     * <p>Returns {@code null} when the call is unauthenticated (e.g., scheduler jobs).
     *
     * @return the authenticated user's UUID, or {@code null}.
     */
    private UUID resolveActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getId();
        }
        return null;
    }

    /**
     * Attempts to extract a UUID entity ID from the method return value.
     *
     * <p>Uses reflection to call {@code getId()} on the return value if present.
     * Returns {@code null} on any failure — entity ID extraction is best-effort.
     *
     * @param result the return value of the intercepted method.
     * @return the entity UUID if extractable, otherwise {@code null}.
     */
    private UUID resolveEntityId(Object result) {
        if (result == null) {
            return null;
        }
        try {
            Object id = result.getClass().getMethod("getId").invoke(result);
            if (id instanceof UUID) {
                return (UUID) id;
            }
        } catch (Exception ignored) {
            // getId() not available on this return type — not an error.
        }
        return null;
    }
}
