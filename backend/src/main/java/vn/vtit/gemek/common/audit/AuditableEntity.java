/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

/**
 * Base class for mutable entities that capture both creator and last-modifier actors.
 *
 * <p>Spring Data {@link AuditingEntityListener} populates {@link #createdBy} on first
 * persist and {@link #updatedBy} on every update, resolving the actor UUID from the
 * {@code auditorAware} bean ({@link SecurityAuditorAware}). When no authenticated
 * {@code UserPrincipal} is present (scheduler / seed / Flyway / login), both stay
 * {@code null} — the columns are nullable by design.
 *
 * <p>This class adds ONLY actor fields. Existing {@code created_at} / {@code updated_at}
 * timestamps remain on each entity's own {@code @PrePersist} / {@code @PreUpdate}
 * callbacks and are intentionally not migrated to Spring Data auditing.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    /** UUID of the user who created the row. Null when no authenticated actor. Set once. */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** UUID of the user who last modified the row. Null when no authenticated actor. */
    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;
}
