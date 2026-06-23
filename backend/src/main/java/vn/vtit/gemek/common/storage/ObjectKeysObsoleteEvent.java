/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import java.util.List;

/**
 * Domain event signalling that one or more MinIO object keys are no longer referenced by any
 * DB row and may be deleted from storage AFTER the surrounding transaction commits.
 *
 * <p>Introduced with C2.2 (announcement media). Publish this inside a transactional service
 * method instead of deleting the object inline — {@link ObsoleteObjectCleanupListener} performs
 * the best-effort delete only on {@code AFTER_COMMIT}, so a rolled-back business operation never
 * destroys a still-referenced object, and a failed storage delete never rolls back the business op
 * (an orphaned object is harmless). Generic by design — reusable for any object surface, not only
 * announcements.
 *
 * @param objectKeys the MinIO object keys to delete after commit.
 */
public record ObjectKeysObsoleteEvent(List<String> objectKeys) {
}
