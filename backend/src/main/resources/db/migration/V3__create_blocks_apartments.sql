-- =============================================================================
-- V3: Create blocks and apartments tables
-- Version: 1.0
-- Date: 2026-05-29
-- Note: apartment_status ENUM was already created in V1.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- TABLE: blocks
-- Building blocks or towers (e.g., Block A, Block B).
-- ---------------------------------------------------------------------------
CREATE TABLE blocks (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100)    NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_blocks      PRIMARY KEY (id),
    CONSTRAINT uq_blocks_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- TABLE: apartments
-- Individual apartment units within a block.
-- ---------------------------------------------------------------------------
CREATE TABLE apartments (
    id          UUID                NOT NULL DEFAULT gen_random_uuid(),
    block_id    UUID                NOT NULL,
    floor       SMALLINT            NOT NULL CHECK (floor >= 0),
    unit_number VARCHAR(20)         NOT NULL,
    area_sqm    NUMERIC(8,2)        CHECK (area_sqm > 0),
    status      apartment_status    NOT NULL DEFAULT 'AVAILABLE',
    notes       TEXT,
    created_at  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_apartments            PRIMARY KEY (id),
    CONSTRAINT fk_apartments_block      FOREIGN KEY (block_id) REFERENCES blocks(id) ON DELETE RESTRICT,
    CONSTRAINT uq_apartments_block_unit UNIQUE (block_id, unit_number)
);

CREATE INDEX idx_apartments_block_id ON apartments (block_id);
CREATE INDEX idx_apartments_floor    ON apartments (floor);
CREATE INDEX idx_apartments_status   ON apartments (status);
