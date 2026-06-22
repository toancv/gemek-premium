-- Residents module tables
-- V4: residents, resident_history, vehicles

CREATE TABLE residents (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    apartment_id        UUID            NOT NULL,
    type                resident_type   NOT NULL,
    move_in_date        DATE            NOT NULL,
    move_out_date       DATE,
    is_primary_contact  BOOLEAN         NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_residents           PRIMARY KEY (id),
    CONSTRAINT fk_residents_user      FOREIGN KEY (user_id)      REFERENCES users(id)      ON DELETE RESTRICT,
    CONSTRAINT fk_residents_apartment FOREIGN KEY (apartment_id) REFERENCES apartments(id) ON DELETE RESTRICT
);
CREATE INDEX idx_residents_user_id      ON residents (user_id);
CREATE INDEX idx_residents_apartment_id ON residents (apartment_id);
CREATE INDEX idx_residents_type         ON residents (type);
CREATE UNIQUE INDEX uq_residents_active_user ON residents (user_id) WHERE move_out_date IS NULL;
CREATE INDEX idx_residents_active       ON residents (apartment_id) WHERE move_out_date IS NULL;

CREATE TABLE resident_history (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    apartment_id        UUID            NOT NULL,
    user_id             UUID            NOT NULL,
    type                resident_type   NOT NULL,
    event               VARCHAR(50)     NOT NULL,
    event_date          DATE            NOT NULL,
    changed_by_user_id  UUID,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_resident_history    PRIMARY KEY (id),
    CONSTRAINT fk_rh_apartment        FOREIGN KEY (apartment_id)       REFERENCES apartments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rh_user             FOREIGN KEY (user_id)            REFERENCES users(id)      ON DELETE RESTRICT,
    CONSTRAINT fk_rh_changed_by       FOREIGN KEY (changed_by_user_id) REFERENCES users(id)      ON DELETE SET NULL
);
CREATE INDEX idx_rh_apartment_id ON resident_history (apartment_id);
CREATE INDEX idx_rh_user_id      ON resident_history (user_id);
CREATE INDEX idx_rh_event_date   ON resident_history (event_date DESC);

CREATE TABLE vehicles (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    resident_id     UUID            NOT NULL,
    apartment_id    UUID            NOT NULL,
    type            vehicle_type    NOT NULL,
    license_plate   VARCHAR(20)     NOT NULL,
    brand           VARCHAR(100),
    model           VARCHAR(100),
    color           VARCHAR(50),
    notes           TEXT,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_vehicles            PRIMARY KEY (id),
    CONSTRAINT fk_vehicles_resident   FOREIGN KEY (resident_id)  REFERENCES residents(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_vehicles_apartment  FOREIGN KEY (apartment_id) REFERENCES apartments(id) ON DELETE RESTRICT,
    CONSTRAINT uq_vehicles_lp         UNIQUE (license_plate)
);
CREATE INDEX idx_vehicles_resident_id   ON vehicles (resident_id);
CREATE INDEX idx_vehicles_apartment_id  ON vehicles (apartment_id);
CREATE INDEX idx_vehicles_type          ON vehicles (type);
CREATE INDEX idx_vehicles_license_plate ON vehicles (license_plate);
