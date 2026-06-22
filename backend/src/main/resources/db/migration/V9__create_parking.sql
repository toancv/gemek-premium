-- =============================================================================
-- V9: Create parking_slots, parking_assignments, and guest_vehicles tables
-- Version: 1.0
-- Date: 2026-05-29
-- =============================================================================

-- ---------------------------------------------------------------------------
-- TABLE: parking_slots
-- Represents a physical parking space in the building.
-- ENUMs parking_slot_type and parking_slot_status are defined in V1.
-- ---------------------------------------------------------------------------

CREATE TABLE parking_slots (
    id          UUID                    NOT NULL DEFAULT gen_random_uuid(),
    slot_number VARCHAR(20)             NOT NULL,
    zone        VARCHAR(50),
    type        parking_slot_type       NOT NULL,
    status      parking_slot_status     NOT NULL DEFAULT 'AVAILABLE',
    notes       TEXT,
    created_at  TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_parking_slots        PRIMARY KEY (id),
    CONSTRAINT uq_parking_slots_number UNIQUE (slot_number)
);

CREATE INDEX idx_ps_type   ON parking_slots (type);
CREATE INDEX idx_ps_status ON parking_slots (status);
CREATE INDEX idx_ps_zone   ON parking_slots (zone);

-- ---------------------------------------------------------------------------
-- TABLE: parking_assignments
-- Links a parking slot to a vehicle and apartment for a date range.
-- An assignment with end_date IS NULL is considered active.
-- ---------------------------------------------------------------------------

CREATE TABLE parking_assignments (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    parking_slot_id     UUID        NOT NULL,
    vehicle_id          UUID        NOT NULL,
    apartment_id        UUID        NOT NULL,
    start_date          DATE        NOT NULL,
    end_date            DATE,
    parking_card_number VARCHAR(50),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_parking_assignments         PRIMARY KEY (id),
    CONSTRAINT fk_pa_slot      FOREIGN KEY (parking_slot_id) REFERENCES parking_slots(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_pa_vehicle   FOREIGN KEY (vehicle_id)      REFERENCES vehicles(id)       ON DELETE RESTRICT,
    CONSTRAINT fk_pa_apartment FOREIGN KEY (apartment_id)    REFERENCES apartments(id)     ON DELETE RESTRICT
);

CREATE INDEX idx_pa_slot_id      ON parking_assignments (parking_slot_id);
CREATE INDEX idx_pa_vehicle_id   ON parking_assignments (vehicle_id);
CREATE INDEX idx_pa_apartment_id ON parking_assignments (apartment_id);
CREATE INDEX idx_pa_active       ON parking_assignments (parking_slot_id) WHERE end_date IS NULL;

-- ---------------------------------------------------------------------------
-- TABLE: guest_vehicles
-- Tracks visitor vehicle entry and exit for security logging.
-- exit_time IS NULL indicates the guest vehicle has not yet departed.
-- ---------------------------------------------------------------------------

CREATE TABLE guest_vehicles (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    license_plate       VARCHAR(20)     NOT NULL,
    owner_name          VARCHAR(255),
    host_apartment_id   UUID            NOT NULL,
    entry_time          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    exit_time           TIMESTAMPTZ,
    purpose             VARCHAR(255),
    recorded_by_user_id UUID,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_guest_vehicles          PRIMARY KEY (id),
    CONSTRAINT fk_gv_apartment FOREIGN KEY (host_apartment_id)    REFERENCES apartments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_gv_user      FOREIGN KEY (recorded_by_user_id)  REFERENCES users(id)      ON DELETE SET NULL
);

CREATE INDEX idx_gv_apartment_id   ON guest_vehicles (host_apartment_id);
CREATE INDEX idx_gv_license_plate  ON guest_vehicles (license_plate);
CREATE INDEX idx_gv_entry_time     ON guest_vehicles (entry_time DESC);
