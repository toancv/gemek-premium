# PROGRESS — Apartment Management System

## Current State
- **Phase:** DONE — all gates approved, deployment prep complete
- **Gate:** G1 ✅ G2 ✅ G3 ✅ G4 ✅ (2026-06-03)
- **Last completed:** 2026-06-05 — GET /api/vehicles: added `search` param (Criteria API, OR on licensePlate/brand/model, null-safe). 9/9 tests pass. ResidentsPage + TicketsPage apartment dropdowns converted from static size=200 to async server-search (loadOptions → GET /api/apartments?size=10&sort=unitNumber&direction=asc); fixes 97 apartments unfindable with current demo data. Apartment create form block dropdown also switched to async SearchableSelect (loadOptions → GET /api/blocks?size=10&sort=name&direction=asc, debounced 300ms). useBlocks (size=200) kept for page-level block filter select; blocksLoading/blockOptions removed. nginx rebuilt.
- **Also 2026-06-05:** Ticket assign form: replaced raw UUID input with async SearchableSelect dropdowns. Staff: 3-call merge (ADMIN+BOARD_MEMBER+TECHNICIAN) — BE only supports single role param. Contractor: shown only for MAINTENANCE_REPAIR, hidden otherwise. Mutual exclusivity enforced. scheduledDate + notes added to payload. Admin: VehiclesPage with async resident SearchableSelect (GET /api/residents?search=&size=20&isActive=true), apartment auto-derived from selected resident (no independent apartment picker), 409→"Biển số đã được đăng ký". Resident: MyVehiclesPage self-scoped via /residents/me (no list calls to /residents or /apartments), unit shown read-only. nginx rebuilt; 201 and 409 verified via curl.
- **Note:** AdminSeeder is idempotent by design — changing ADMIN_PASSWORD in .env after the admin exists requires scripts/reset-admin-password.sql (or docker compose down -v) to update the stored BCrypt hash.
- **Blocked:** None

---

## Completed Modules
| Module | Phase | Tests | Committed |
|--------|-------|-------|-----------|
| System Architecture v2 | architect | N/A | Yes |
| DB Schema v2 | architect | N/A | Yes |
| API Spec v2 | architect | N/A | Yes |
| Auth + RBAC | backend | 3 tests | Yes (bb08316) |
| Apartments & Blocks | backend | 4 tests | Yes (c4b6f00) |
| Residents & Vehicles | backend | 5 tests (Docker req.) | Yes (424df57) |
| Ticket Management | backend | 8 tests | Yes (5dcb446) |
| Contractors & Contracts | backend | 5 tests | Yes (ce782f9) |
| Announcements | backend | 4 tests | Yes (53cae29) |
| Amenity Booking | backend | 5 tests | Yes (8863449) |
| Parking | backend | 4 tests | Yes (dc19526) |
| Notifications + Audit Log | backend | 3 tests | Yes (50e07a5) |
| Admin Portal (12 pages) | frontend | N/A | Yes (ba1c634) |
| Resident Portal (9 pages) | frontend | N/A | Yes (ba1c634) |
| Shared UI Package | frontend | N/A | Yes (ba1c634) |

---

## Approved Gates
| Gate | Approved | CTO Notes |
|------|---------|-----------|
| G1 — Techstack | ✅ 2026-05-29 | |
| G2 — Backend | ✅ 2026-05-29 | |
| G3 — Frontend | ✅ 2026-05-29 | SAST backend+frontend both PASS WITH NOTES before approval |
| G4 — Testing | ✅ 2026-06-03 | 149/149 tests pass, security audit 19/20 fixed, SEC-20 deferred, app boots fresh DB |

---

## Backend Module Queue
| # | Module | Status | Committed |
|---|--------|--------|-----------|
| 0 | Project scaffold (pom.xml, docker-compose, Flyway base) | ✅ done | Yes |
| 1 | Auth + RBAC | ✅ done | Yes |
| 2 | Apartments & Blocks | ✅ done | Yes |
| 3 | Residents & Vehicles | ✅ done | Yes |
| 4 | Ticket Management | ✅ done | Yes |
| 5 | Contractors & Contracts | ✅ done | Yes |
| 6 | Announcements | ✅ done | Yes |
| 7 | Amenity Booking | ✅ done | Yes |
| 8 | Parking | ✅ done | Yes |
| 9 | Reports & Dashboard | ✅ done | Yes |
| 10 | Notifications + Audit Log | ✅ done | Yes |

---

## Session Resume Instructions
If context is lost, read these files in order:
1. `PROGRESS.md` (this file) — current state
2. `DECISIONS.md` — all decisions made
3. `docs/ARCHITECTURE.md` — system design
4. `docs/API-SPEC.md` — API contracts
5. `docs/DB-SCHEMA.sql` — database schema
6. Then continue from "Current State" above
