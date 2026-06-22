# P5 — API-SPEC ↔ Controller Reconciliation

**Date:** 2026-06-18
**Scope:** DOCS ONLY. Diff `docs/API-SPEC.md` (was v2.1) against the actual controller layer
(`backend/.../module/**/*Controller.java`). Code is ground truth. No production/schema change.
**Result:** API-SPEC bumped to v2.2. 10 endpoints added (in code, missing from spec); 10 contract
mismatches corrected; 4 stale spec entries flagged for CTO ruling (NOT deleted).

---

## Method — ground truth

Enumerated every `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@RequestMapping` +
`@PreAuthorize` + `@RequestParam/@PathVariable` across all 14 controllers, plus the
`public ResponseEntity<…>` return type of each missing endpoint (response shapes sourced from the
controller's DTO, reusing the already-documented object where the same DTO is returned).

Sprint-focus items confirmed ALREADY documented (no action): `GET /api/auth/me`,
`PUT /api/auth/me/password`, `PUT /api/auth/me/profile`, `GET /api/tickets?overdue` (P2.6),
`GET /api/tickets?mine` (P2.8), and the full `/api/users` surface (P1: GET list, POST, GET/{id},
PUT/{id}, DELETE/{id}, PUT/{id}/reset-password).

---

## A. MISSING from spec — present in code → ADDED (sourced from controller)

| # | Method + path | Role (`@PreAuthorize`) | Controller:line | Response (DTO) |
|---|---|---|---|---|
| 1 | `GET /api/residents/me` | `RESIDENT` | ResidentController:67 | `ResidentResponse` (= resident detail shape) |
| 2 | `GET /api/vehicles/{id}` | `ADMIN,RESIDENT` | VehicleController:111 | `VehicleResponse` (= vehicle list item) |
| 3 | `GET /api/amenities/{id}` | authenticated | AmenityController:131 | `AmenityResponse` (= amenity list item) |
| 4 | `GET /api/parking/slots/{id}` | `ADMIN,TECHNICIAN` | ParkingController:124 | `ParkingSlotResponse` (= slot list item) |
| 5 | `DELETE /api/parking/slots/{id}` | `ADMIN` | ParkingController:156 | `204 No Content` |
| 6 | `GET /api/parking/assignments/{id}` | `ADMIN` | ParkingController:239 | `ParkingAssignmentResponse` (= assignment item) |
| 7 | `DELETE /api/tickets/{id}/photos/{photoId}` | `ADMIN,TECHNICIAN` | TicketController:283 | `204 No Content` |
| 8 | `GET /api/notifications/unread-count` | authenticated | NotificationController:143 | `{ "unreadCount": <long> }` |
| 9 | `GET /api/contractors/{id}/contracts` | `ADMIN,BOARD_MEMBER` | ContractorController:168 | `PageResponse<ContractResponse>` (= contract list item) |
| 10 | `POST /api/contractors/{id}/contracts` | `ADMIN` | ContractorController:190 | `ContractResponse` (= contract summary) |

Notes:
- #8 response field name is `unreadCount` (verified `UnreadCountResponse.builder().unreadCount(...)`,
  NotificationController:149). Spec previously only *mentioned* this endpoint inside the
  `GET /api/notifications` note without a dedicated section.
- #9/#10 are the as-built contract LIST + CREATE surface (nested under contractor). They replace the
  stale top-level `GET /api/contracts` / `POST /api/contracts` spec entries — see §C S2/S3.

---

## B. CONTRACT MISMATCHES — corrected spec to match code

| # | Endpoint | Spec said | Code says | Fix applied |
|---|---|---|---|---|
| A | `GET /api/parking/slots` | role `ADMIN` | `ADMIN,TECHNICIAN` (ParkingController:85) | role widened in spec |
| B | parking guest surface | `GET/POST /api/parking/guest-vehicles`, `PUT …/{id}/exit` | `GET/POST /api/parking/guests`, `PUT …/{id}/checkout` (ParkingController:259/281/299) | **paths renamed in spec**; GET role `ADMIN`→`ADMIN,TECHNICIAN`; GET params `from,to,licensePlate`→`apartmentId,active` |
| C | `GET /api/parking/assignments` | params `slotId,vehicleId,apartmentId,isActive` | `apartmentId,slotId,active` (no `vehicleId`) (ParkingController:217) | params corrected; `isActive`→`active` |
| D | `GET /api/vehicles` | params `apartmentId,residentId,type,licensePlate,isActive,search` | only `apartmentId,search` bound (VehicleController:66) | trimmed spec params to as-built |
| E | `GET /api/amenity-bookings` | role `ADMIN,RESIDENT` | `ADMIN,TECHNICIAN,BOARD_MEMBER,RESIDENT` (AmenityController:215) | role widened in spec |
| F | `GET /api/contractors` | role `ADMIN,BOARD_MEMBER` | `ADMIN,TECHNICIAN,BOARD_MEMBER` (ContractorController:78) | role widened |
| G | `GET /api/contractors/{id}` | role `ADMIN,BOARD_MEMBER` | `ADMIN,TECHNICIAN,BOARD_MEMBER` (ContractorController:117) | role widened |
| H | `GET /api/contracts/{id}` | role `ADMIN,BOARD_MEMBER` | `ADMIN,BOARD_MEMBER,TECHNICIAN` (ContractorController:211) | role widened |
| I | `GET /api/contracts/{id}/payments` | role `ADMIN,BOARD_MEMBER` | `ADMIN` only (ContractorController:244) | role narrowed to match code |
| J | `GET /api/contracts/{id}/schedules` | role `ADMIN` | `ADMIN,TECHNICIAN` (ContractorController:280) | role widened |

Non-issue (no change): `GET /api/amenities` spec "Any authenticated role" vs code
`ADMIN,TECHNICIAN,RESIDENT,BOARD_MEMBER` — the four roles ARE the full authenticated set; equivalent.

---

## C. STALE — in spec, NOT in code → FLAGGED for CTO ruling (left in spec, NOT deleted)

| # | Stale spec entry | Evidence | Likely status | Recommendation |
|---|---|---|---|---|
| S1 | `GET /api/contractors/{id}/work-history` | no controller mapping | superseded by `GET /api/contractors/{id}/contracts` (added #9)? OR genuinely dropped | **await ruling** — keep or delete |
| S2 | `GET /api/contracts` (top-level list w/ filters) | no controller mapping | superseded by nested `GET /api/contractors/{id}/contracts` (#9) | **await ruling** |
| S3 | `POST /api/contracts` (top-level create) | no controller mapping | superseded by nested `POST /api/contractors/{id}/contracts` (#10) | **await ruling** |
| S4 | `PUT /api/maintenance-schedules/{id}` | no controller mapping | not built (schedules are create+list only in code) | **await ruling** |

Already self-flagged in spec (NOT counted as new findings):
- `POST/GET /api/contracts/{id}/attachment` — spec already marks "NOT IMPLEMENTED + R-4 gate".
- ticket query param `slaBreached` — spec already marks "spec-only placeholder, NOT implemented".

> CTO ruling needed: confirm S1–S3 are renamed/superseded (then delete from spec next pass) vs.
> planned-but-unbuilt (then keep + mark NOT IMPLEMENTED like the attachment endpoints). S4 likewise.
> Per task discipline these were NOT deleted without confirmation.

---

## D. Edits applied to API-SPEC.md

- Header: `**Version:** 2.1` → `2.2`; `**Date:** 2026-06-08` → `2026-06-18`; added a one-line
  reconciliation banner referencing this report.
- §1 Auth: confirmed `GET /me`, `PUT /me/password`, `PUT /me/profile` present (no change).
- §4 Residents: added `GET /api/residents/me` (#1).
- §5 Vehicles: added `GET /api/vehicles/{id}` (#2); trimmed list params (mismatch D).
- §6 Tickets: added `DELETE /api/tickets/{id}/photos/{photoId}` (#7).
- §7 Amenities: added `GET /api/amenities/{id}` (#3); widened `GET /api/amenity-bookings` role (E).
- §8 Contractors/Contracts: added `GET`+`POST /api/contractors/{id}/contracts` (#9,#10);
  corrected roles F/G/H/I/J; inline stale-flag pointers on S1–S4.
- §9 Parking: added `GET`+`DELETE /api/parking/slots/{id}` (#4,#5), `GET /api/parking/assignments/{id}`
  (#6); widened `GET /slots` role (A); renamed guest-vehicle paths + fixed role/params (B); fixed
  assignments params (C).
- §12 Notifications: added `GET /api/notifications/unread-count` (#8).

All additions sourced from controller + DTO. No response field invented; where a shape is reused it
points to the already-documented object.

---

## §C RESOLVED (2026-06-18) — see `reports/c-p5-stale-resolution.md`

CTO ruling applied per entry (strict duplicate test against controller code):
- S1 work-history → **KEEP [PLANNED]** (no tickets-by-contractor endpoint; `GET /api/tickets` has no `assignedToContractorId` filter).
- S2 contracts list → **KEEP [PLANNED]** (nested `/contractors/{id}/contracts` is per-contractor, no system-wide filtered list).
- S3 contracts create → **REMOVED** (duplicate of `POST /api/contractors/{id}/contracts`, ContractorController:190).
- S4 maintenance-schedules PUT → **KEEP [PLANNED]** (schedules are create+list only; no update path).

The 3 KEEPs are now marked `🚧 [PLANNED — chưa implement]` in API-SPEC so future reconciliation does not re-flag them as stale.
