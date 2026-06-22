-- V18__add_contract_announcement_updated_by.sql
-- AUD.2 — converge contracts / announcements onto Spring Data JPA auditing.
--
-- V17 deliberately skipped these two tables because they already carry a creator
-- column (created_by_user_id). AUD.2 now:
--   * adds the missing updated_by actor column (wired to @LastModifiedBy), and
--   * keeps created_by_user_id as-is (CTO ruling: NO rename) — the entity field
--     becomes a plain @CreatedBy UUID mapped to that existing column.
--
-- Column shape: nullable uuid. System / seed / scheduler writes have no authenticated
-- actor, so the column MUST accept NULL (AuditorAware returns empty). No backfill.
--
-- FK: real FK to users(id) ON DELETE SET NULL, matching the existing creator-column
-- precedent. Integrity at the DB layer; the app keeps a cheap UUID actor (no entity
-- load on write).
--
-- NOT touched: created_by_user_id (kept), created_at / updated_at, any other table.

ALTER TABLE contracts ADD COLUMN updated_by uuid;
ALTER TABLE contracts
    ADD CONSTRAINT fk_contracts_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE announcements ADD COLUMN updated_by uuid;
ALTER TABLE announcements
    ADD CONSTRAINT fk_announcements_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;
