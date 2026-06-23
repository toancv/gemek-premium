-- V20 — Relax uq_residents_active_user: single-active-per-user -> active-per-(user, apartment).
--
-- BEFORE (V4): CREATE UNIQUE INDEX uq_residents_active_user ON residents (user_id)
--              WHERE move_out_date IS NULL;
--   This partial unique index allowed at most ONE active residency per user across ALL
--   apartments, forbidding concurrent multi-residency entirely.
--
-- AFTER (this migration): the same index name now keys on (user_id, apartment_id), still
--   partial on the active rows. This ENABLES the CTO-approved concurrent multi-residency
--   requirement — one user may hold >=2 active residents rows in DIFFERENT apartments at the
--   same time — while STILL forbidding two active rows for the SAME (user, apartment) pair
--   (the real invariant: a person cannot be "actively residing" in one apartment twice).
--
-- SAFE because P1 (the findActiveByUserId sweep, DECISIONS.md "Residency lifecycle — phased
-- plan": P0 docs -> P1 sweep -> P2 index relax) already converted every consumer of the
-- singular Optional-returning findActiveByUserId to multi-residency-safe queries
-- (findAllActiveByUserId / findActiveApartmentIdsByUserId / existsActiveByUserIdAndApartmentId).
-- No code path throws NonUniqueResultException under a 2-active-row state. The sweep landed
-- and was verified green BEFORE this relax, honoring the hard sweep-before-relax ordering.
--
-- INDEX-only change: no table/column drop, no data DML. The pre-flight check confirmed the
-- live dev DB holds no duplicate active (user_id, apartment_id) pair, so the new unique index
-- builds without violation.

DROP INDEX uq_residents_active_user;
CREATE UNIQUE INDEX uq_residents_active_user ON residents (user_id, apartment_id) WHERE move_out_date IS NULL;
