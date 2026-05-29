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
import org.springframework.stereotype.Component;

/**
 * AOP aspect stub for {@link Auditable}-annotated service methods.
 *
 * <p>This is a stub implementation. The full implementation — which writes rows
 * to the {@code audit_logs} table — will be added when the {@code audit_logs}
 * migration is introduced in a later module. Annotated methods proceed normally
 * and the audit event is logged at DEBUG level until then.
 *
 * <p>The annotation and aspect are defined here so all modules can use
 * {@code @Auditable} without requiring a refactor pass later.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    /**
     * Intercepts all {@link Auditable}-annotated service methods.
     *
     * <p>Logs the action at DEBUG level and proceeds with the method invocation.
     * The full audit persistence logic will replace this stub body in a later sprint.
     *
     * @param joinPoint the intercepted method join point.
     * @param auditable the {@link Auditable} annotation instance.
     * @return the result of the intercepted method.
     * @throws Throwable if the intercepted method throws.
     */
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        log.debug("Audit stub — action={}, entityType={}, method={}",
                auditable.action(), auditable.entityType(), joinPoint.getSignature().toShortString());
        return joinPoint.proceed();
    }
}
