/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as auditable.
 *
 * <p>Methods annotated with {@code @Auditable} are intercepted by
 * {@link AuditLogAspect}, which records the action, entity type, entity ID,
 * and old/new values in the {@code audit_logs} table.
 *
 * <p>The full implementation of the aspect is deferred to the module that
 * introduces the {@code audit_logs} table migration. This annotation is
 * created now so modules can be annotated in place without refactoring later.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * The action label recorded in the audit log (e.g., "CREATE", "UPDATE", "DELETE").
     *
     * @return the action string.
     */
    String action() default "";

    /**
     * The entity type recorded in the audit log (e.g., "User", "Ticket").
     *
     * @return the entity type string.
     */
    String entityType() default "";
}
