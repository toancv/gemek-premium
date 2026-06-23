/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Deletes obsolete MinIO objects AFTER the transaction that orphaned them commits.
 *
 * <p>Listens for {@link ObjectKeysObsoleteEvent} only on {@link TransactionPhase#AFTER_COMMIT}:
 * if the publishing transaction rolls back, no delete runs (the rows still exist). Deletion is
 * best-effort — {@link FileStorageService#delete} already logs and swallows storage failures, and
 * a leftover object is harmless — so a storage outage never propagates back into the business flow.
 */
@Component
public class ObsoleteObjectCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ObsoleteObjectCleanupListener.class);

    private final FileStorageService fileStorageService;

    /**
     * Constructs the listener with the storage service.
     *
     * @param fileStorageService the MinIO-backed storage service.
     */
    public ObsoleteObjectCleanupListener(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Best-effort deletes each obsolete object key after the orphaning transaction commits.
     *
     * @param event the obsolete-object-keys event published inside the committed transaction.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onObjectKeysObsolete(ObjectKeysObsoleteEvent event) {
        List<String> keys = event.objectKeys();
        if (keys == null || keys.isEmpty()) {
            return;
        }
        log.info("After-commit cleanup — deleting {} obsolete object(s).", keys.size());
        // Each delete is independent and self-logging; one failure must not abort the rest.
        for (String key : keys) {
            fileStorageService.delete(key);
        }
    }
}
