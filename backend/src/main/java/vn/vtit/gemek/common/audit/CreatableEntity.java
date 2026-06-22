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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

/**
 * Base class for append-only entities that capture the creator actor only.
 *
 * <p>These tables have no update path, so they get {@link #createdBy} but deliberately
 * no {@code updatedBy} field/column. Spring Data {@link AuditingEntityListener} populates
 * {@link #createdBy} on first persist from the {@code auditorAware} bean
 * ({@link SecurityAuditorAware}); it stays {@code null} when no authenticated actor exists.
 *
 * <p>Existing {@code created_at} timestamps remain on each entity's own
 * {@code @PrePersist} callback and are not migrated to Spring Data auditing.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class CreatableEntity {

    /** UUID of the user who created the row. Null when no authenticated actor. Set once. */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
