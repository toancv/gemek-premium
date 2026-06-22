-- V19 — Normalize apartment occupancy: OCCUPIED becomes a derived-only value.
--
-- Occupancy (AVAILABLE vs OCCUPIED) is now DERIVED at query time from active residents
-- (residents with move_out_date IS NULL); see OccupancyResolver. The stored
-- apartments.status column must therefore only ever hold AVAILABLE or MAINTENANCE.
--
-- A small number of rows were manually set to OCCUPIED before this change. Reset them to
-- AVAILABLE so the stored column no longer carries the now-derived-only OCCUPIED value;
-- their effective status is recomputed correctly from residents. MAINTENANCE rows are left
-- untouched (MAINTENANCE remains a stored, manually-set state with priority over occupancy).
--
-- The OCCUPIED enum value is intentionally kept in the apartment_status type — it is still a
-- valid computed/response value, just never a stored one. No data-fix is needed for the rows
-- that wrongly showed AVAILABLE despite residents: derivation fixes those automatically.

UPDATE apartments
SET status = 'AVAILABLE'
WHERE status = 'OCCUPIED';
