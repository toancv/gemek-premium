/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code @CreatedBy} / {@code @LastModifiedBy} are
 * populated on persist/update.
 *
 * <p>The actor UUID is resolved by the {@code auditorAware} bean
 * ({@link vn.vtit.gemek.common.audit.SecurityAuditorAware}). Auditing is opt-in per entity
 * via the {@code @EntityListeners(AuditingEntityListener.class)} on the base classes
 * ({@link vn.vtit.gemek.common.audit.AuditableEntity},
 * {@link vn.vtit.gemek.common.audit.CreatableEntity}).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {
}
