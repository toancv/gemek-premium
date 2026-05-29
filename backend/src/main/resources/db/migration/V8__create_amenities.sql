-- =============================================================================
-- V8 — Amenity Booking module
-- Creates: amenities, amenity_bookings
-- =============================================================================

CREATE TABLE amenities (
    id                              UUID            NOT NULL DEFAULT gen_random_uuid(),
    name                            VARCHAR(100)    NOT NULL,
    description                     TEXT,
    location                        VARCHAR(255),
    capacity                        SMALLINT        CHECK (capacity > 0),
    opening_time                    TIME            NOT NULL DEFAULT '06:00',
    closing_time                    TIME            NOT NULL DEFAULT '22:00',
    max_daily_bookings_per_resident SMALLINT        NOT NULL DEFAULT 1 CHECK (max_daily_bookings_per_resident > 0),
    requires_approval               BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active                       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_amenities      PRIMARY KEY (id),
    CONSTRAINT uq_amenities_name UNIQUE (name),
    CONSTRAINT chk_amenity_hours CHECK (opening_time < closing_time)
);

CREATE TABLE amenity_bookings (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    amenity_id          UUID            NOT NULL,
    resident_id         UUID            NOT NULL,
    apartment_id        UUID            NOT NULL,
    booking_date        DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME            NOT NULL,
    status              booking_status  NOT NULL DEFAULT 'PENDING',
    notes               TEXT,
    rejection_reason    TEXT,
    approved_by_user_id UUID,
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_amenity_bookings           PRIMARY KEY (id),
    CONSTRAINT fk_ab_amenity    FOREIGN KEY (amenity_id)            REFERENCES amenities(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_ab_resident   FOREIGN KEY (resident_id)           REFERENCES residents(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_ab_apartment  FOREIGN KEY (apartment_id)          REFERENCES apartments(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_ab_approver   FOREIGN KEY (approved_by_user_id)   REFERENCES users(id)       ON DELETE SET NULL,
    CONSTRAINT chk_ab_times     CHECK (start_time < end_time)
);

CREATE INDEX idx_ab_amenity_id   ON amenity_bookings (amenity_id);
CREATE INDEX idx_ab_resident_id  ON amenity_bookings (resident_id);
CREATE INDEX idx_ab_apartment_id ON amenity_bookings (apartment_id);
CREATE INDEX idx_ab_status       ON amenity_bookings (status);
CREATE INDEX idx_ab_date_amenity ON amenity_bookings (amenity_id, booking_date, status);

-- =============================================================================
-- Seed: default amenities for a Vietnamese apartment complex
-- =============================================================================
INSERT INTO amenities (name, description, location, capacity, opening_time, closing_time,
                       max_daily_bookings_per_resident, requires_approval, is_active)
VALUES
    ('Gym / Fitness Center',  'Fully equipped fitness center', 'Floor 2',  20, '05:30', '22:00', 1, FALSE, TRUE),
    ('Swimming Pool',         'Outdoor rooftop pool',          'Rooftop',  30, '06:00', '21:00', 1, FALSE, TRUE),
    ('BBQ Area',              'Outdoor BBQ and dining area',   'Rooftop',  20, '10:00', '22:00', 1, TRUE,  TRUE),
    ('Meeting Room A',        'Conference room, 10 seats',     'Floor 1',  10, '08:00', '21:00', 2, TRUE,  TRUE),
    ('Meeting Room B',        'Conference room, 6 seats',      'Floor 1',   6, '08:00', '21:00', 2, TRUE,  TRUE),
    ('Kids Playground',       'Outdoor playground area',       'Ground',  NULL,'07:00', '20:00', 1, FALSE, TRUE);
