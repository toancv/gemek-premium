-- ---------------------------------------------------------------------------
-- V13 — N3 event notifications: new notification_type enum values.
--
-- This file must contain ONLY ALTER TYPE statements: a value added by
-- ALTER TYPE ... ADD VALUE cannot be used inside the same transaction,
-- so no seed rows or dependent DDL may live in this migration.
-- ---------------------------------------------------------------------------

ALTER TYPE notification_type ADD VALUE 'TICKET_CREATED';
ALTER TYPE notification_type ADD VALUE 'TICKET_SLA_WARNING';
ALTER TYPE notification_type ADD VALUE 'HOUSEHOLD_MEMBER_ADDED';
ALTER TYPE notification_type ADD VALUE 'TICKET_RATING_REQUESTED';
