-- V15 — N3 P5: creator-chosen public/private flag on tickets.
-- Existing rows stay private (CTO decision: default private; G3 — immutable after create).
ALTER TABLE tickets ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tickets.is_public IS
    'Creator-chosen community visibility. Immutable after create (G3). Public tickets are readable by all residents in REDACTED form; photos remain household/staff-only pending F-05.';
