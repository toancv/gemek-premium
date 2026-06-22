# Test Gap Fill — Final Summary Report
**Branch:** improvement/security  
**Date:** 2026-06-02  
**Tool:** JaCoCo 0.8.12

---

## Counts

| Category | Count |
|----------|-------|
| Total gaps audited | 14 |
| DONE | 12 |
| SKIP (low-risk, no test needed) | 1 |
| BUG-FOUND | 0 |
| BLOCKED | 0 |

---

## Gap Table

| Gap ID | Target Class / Method | Priority | Final Status | Test File | Note |
|--------|-----------------------|----------|--------------|-----------|------|
| GAP-01 | `FileController.presign` / `TicketService.assertPresignAccess` | CRITICAL | DONE | `FileControllerTest.java` | presign ownership rejection + MIME type guard |
| GAP-02 | `TicketServiceImpl.getTicketDetail` (phone strip) / `assertPresignAccess` | CRITICAL | DONE | `TicketServiceImplTest.java` | SEC-03/08: TECHNICIAN/BOARD_MEMBER phone=null |
| GAP-03 | `UserServiceImpl.updateUser` (role change) | HIGH | DONE | `UserServiceImplTest.java` | SEC-04: role promotion audit log verified |
| GAP-04 | `AmenityServiceImpl.createBooking` guards | HIGH | DONE | `AmenityControllerTest.java` (+3 tests) | SEC-11/12/22: past-date / advance-window / duration guards |
| GAP-05 | `AuthServiceImpl.logout` (null/blank token) | HIGH | DONE | `AuthServiceTest.java` (+2 tests) | SEC-13: null token guard |
| GAP-06 | `ParkingController` GET endpoints | HIGH | DONE | `ParkingControllerTest.java` (+4 tests) | list slots, assignments, guests + non-ADMIN 403 |
| GAP-07 | `ContractorServiceImpl` lifecycle | MEDIUM | DONE | `ContractorServiceImplTest.java` (8 tests) | PENDING→ACTIVE/TERMINATED transitions + 6 NOT_FOUND guards |
| GAP-08 | `ResidentServiceImpl` assignment guards | MEDIUM | DONE | `ResidentServiceImplTest.java` (7 tests) | dup-active CONFLICT, double-moveout CONFLICT, NOT_FOUND, RESIDENT cross-record FORBIDDEN |
| GAP-09 | `MaintenanceScheduleRunner` / `ContractExpiryScheduler` | MEDIUM | DONE | `MaintenanceScheduleRunnerTest.java` + `ContractExpirySchedulerTest.java` | scheduled job branch coverage |
| GAP-10 | `AnnouncementServiceImpl` access control | MEDIUM | DONE | `AnnouncementServiceImplTest.java` (8 tests) | RESIDENT draft NOT_FOUND, published CONFLICT, SEC-07 markRead draft guard, scope VALIDATION_ERROR |
| GAP-11 | `ApartmentServiceImpl` status transitions | MEDIUM | DONE | `ApartmentServiceImplTest.java` (6 tests) | RESIDENT cross-apt FORBIDDEN, NOT_FOUND, dup-unit CONFLICT, active-residents delete CONFLICT |
| GAP-12 | Mapper classes | LOW | SKIP | — | MapStruct-generated; no hand-written logic; risk too low to justify tests |
| GAP-13 | `SecurityConfig` CORS rejection | LOW | DONE | `CorsIntegrationTest.java` (3 tests) | non-allowlisted origin → 403 + no ACAO header; allowlisted origin → 200 + correct ACAO |
| GAP-14 | Systemic branch gap | LOW | DONE | — | Covered by negative-path tests added in GAP-07 through GAP-13 |

---

## BUG-FOUND

None. No production defects discovered during gap filling. All guards behaved as specified.

---

## BLOCKED

None.

---

## Coverage Delta

| Metric | Before (83 tests) | After (149 tests) | Delta |
|--------|-------------------|-------------------|-------|
| Line coverage | 65.1% (2106/3234) | **70.6%** (2282/3234) | +5.5 pp |
| Branch coverage | 33.7% | **42.3%** (315/744) | +8.6 pp |
| Method coverage | — | 70.3% (426/606) | — |
| Class coverage | — | 87.9% (131/149) | — |

Coverage measured via `mvn verify` (JaCoCo 0.8.12) on the full 149-test suite.  
Both line and branch coverage remain below the ECC 80% threshold. Reaching 80% would require
positive-path integration tests for the report, fee, and notification modules (currently untested).

---

## Suite Health

**149 tests — 149 passed, 0 failed, 0 errors, 0 skipped.**  
`mvn verify` exits `BUILD SUCCESS`.

---

## New Test Files Added This Cycle

| File | Tests | Gap |
|------|-------|-----|
| `common/storage/FileControllerTest.java` | — | GAP-01 |
| `module/ticket/TicketServiceImplTest.java` | 8 | GAP-02 |
| `module/user/UserServiceImplTest.java` | 5 | GAP-03 |
| `module/amenity/AmenityControllerTest.java` | +3 added | GAP-04 |
| `module/auth/AuthServiceTest.java` | +2 added | GAP-05 |
| `module/parking/ParkingControllerTest.java` | +4 added | GAP-06 |
| `module/contractor/ContractorServiceImplTest.java` | 8 | GAP-07 |
| `module/resident/ResidentServiceImplTest.java` | 7 | GAP-08 |
| `scheduler/MaintenanceScheduleRunnerTest.java` | 5 | GAP-09 |
| `scheduler/ContractExpirySchedulerTest.java` | 4 | GAP-09 |
| `module/announcement/AnnouncementServiceImplTest.java` | 8 | GAP-10 |
| `module/apartment/ApartmentServiceImplTest.java` | 6 | GAP-11 |
| `common/CorsIntegrationTest.java` | 3 | GAP-13 |
