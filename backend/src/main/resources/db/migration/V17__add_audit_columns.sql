-- V17__add_audit_columns.sql
-- Adopt Spring Data JPA auditing: add actor columns (created_by / updated_by) wired
-- to @CreatedBy / @LastModifiedBy. Existing created_at / updated_at are NOT touched.
--
-- Column shape: nullable uuid. System / seed / scheduler / Flyway / login writes have
-- no authenticated actor, so the actor column MUST accept NULL (AuditorAware returns empty).
-- No backfill: historical authorship is genuinely unknown.
--
-- FK: real FK to users(id) ON DELETE SET NULL, matching the existing creator-column
-- precedent (V6 contracts/payments, V10 audit_logs). Integrity at the DB layer; the app
-- layer keeps a cheap UUID actor (no entity load on write).
--
-- NOTE (prod): adding a validated FK scans the table. On a large prod table prefer
-- ADD CONSTRAINT ... NOT VALID followed by a later VALIDATE CONSTRAINT. Dev volumes are
-- small, so the in-line validated FK below is fine here.
--
-- NOT touched here: contracts / announcements (they already carry created_by_user_id;
-- AUD.2 adds their updated_by and converges them), and the 4 excluded tables
-- (ticket_status_history, ticket_photos, announcement_reads, audit_logs).

-- ---------------------------------------------------------------------------
-- 12 mutable tables get BOTH created_by + updated_by.
-- ---------------------------------------------------------------------------

ALTER TABLE users ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE users
    ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE blocks ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE blocks
    ADD CONSTRAINT fk_blocks_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_blocks_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE apartments ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE apartments
    ADD CONSTRAINT fk_apartments_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_apartments_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE residents ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE residents
    ADD CONSTRAINT fk_residents_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_residents_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE vehicles ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE vehicles
    ADD CONSTRAINT fk_vehicles_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_vehicles_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE contractors ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE contractors
    ADD CONSTRAINT fk_contractors_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_contractors_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE maintenance_schedules ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE maintenance_schedules
    ADD CONSTRAINT fk_maintenance_schedules_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_maintenance_schedules_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE tickets ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_tickets_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE amenities ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE amenities
    ADD CONSTRAINT fk_amenities_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_amenities_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE amenity_bookings ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE amenity_bookings
    ADD CONSTRAINT fk_amenity_bookings_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_amenity_bookings_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE parking_slots ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE parking_slots
    ADD CONSTRAINT fk_parking_slots_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_parking_slots_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE parking_assignments ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE parking_assignments
    ADD CONSTRAINT fk_parking_assignments_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_parking_assignments_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- 5 append-only tables get created_by ONLY (no update path -> no updated_by).
-- ---------------------------------------------------------------------------

ALTER TABLE resident_history ADD COLUMN created_by uuid;
ALTER TABLE resident_history
    ADD CONSTRAINT fk_resident_history_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE contract_payments ADD COLUMN created_by uuid;
ALTER TABLE contract_payments
    ADD CONSTRAINT fk_contract_payments_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE guest_vehicles ADD COLUMN created_by uuid;
ALTER TABLE guest_vehicles
    ADD CONSTRAINT fk_guest_vehicles_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE notifications ADD COLUMN created_by uuid;
ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE notification_subscriptions ADD COLUMN created_by uuid;
ALTER TABLE notification_subscriptions
    ADD CONSTRAINT fk_notification_subscriptions_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
