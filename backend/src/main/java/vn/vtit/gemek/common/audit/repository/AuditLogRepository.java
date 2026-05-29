/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.vtit.gemek.common.audit.entity.AuditLog;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link AuditLog} entity.
 *
 * <p>Standard CRUD is sufficient for the current module. Future phases may add
 * query methods for admin audit-trail search (by user, entity type, date range).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
