-- ---------------------------------------------------------------------------
-- V14 — N3 P2: notification_subscriptions (participant/thread model, design §B B1).
--
-- Explicit membership rows: CREATOR (written at ticket create), ASSIGNEE
-- (written at assign), FOLLOWER (written via the follow endpoint, P5).
-- Admins intentionally get NO rows — they receive only TICKET_CREATED and
-- SLA escalations, never per-thread updates.
--
-- entity_id has NO foreign key ON PURPOSE: the table is polymorphic across
-- entity types (entity_type carries the same label space as
-- notifications.reference_type, e.g. 'Ticket'); N4 comments and future
-- entities reuse it without schema change.
-- ---------------------------------------------------------------------------

CREATE TABLE notification_subscriptions (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID            NOT NULL,
    entity_type VARCHAR(100)    NOT NULL,
    entity_id   UUID            NOT NULL,
    joined_via  VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_notification_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_notification_subscriptions_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_subscriptions UNIQUE (user_id, entity_type, entity_id),
    CONSTRAINT ck_notification_subscriptions_joined_via
        CHECK (joined_via IN ('CREATOR', 'ASSIGNEE', 'FOLLOWER'))
);

-- Dispatch lookup: all participants of one entity.
CREATE INDEX idx_notification_subscriptions_entity
    ON notification_subscriptions (entity_type, entity_id);

-- User-side queries (my followed threads; cascade-delete support).
CREATE INDEX idx_notification_subscriptions_user
    ON notification_subscriptions (user_id);

-- ---------------------------------------------------------------------------
-- Backfill existing tickets. Plain DML on a just-created table — safe in the
-- same migration file (unlike ALTER TYPE values, V13).
-- Order matters: creators first; a user who is both creator and assignee of
-- the same ticket keeps CREATOR (second insert hits the unique constraint).
-- ---------------------------------------------------------------------------

INSERT INTO notification_subscriptions (user_id, entity_type, entity_id, joined_via)
SELECT t.submitted_by_user_id, 'Ticket', t.id, 'CREATOR'
FROM tickets t
ON CONFLICT (user_id, entity_type, entity_id) DO NOTHING;

INSERT INTO notification_subscriptions (user_id, entity_type, entity_id, joined_via)
SELECT t.assigned_to_user_id, 'Ticket', t.id, 'ASSIGNEE'
FROM tickets t
WHERE t.assigned_to_user_id IS NOT NULL
ON CONFLICT (user_id, entity_type, entity_id) DO NOTHING;
