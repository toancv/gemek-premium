# Test Gap Progress — improvement/security branch

Updated: 2026-06-02 — 149 tests green (was 83). +66 new tests written.

| Gap ID | Target Class/Method | Priority | Status | Test File | Note |
|--------|---------------------|----------|--------|-----------|------|
| GAP-01 | FileController.presign / TicketService.assertPresignAccess | CRITICAL | DONE | FileControllerTest.java | presign ownership rejection + MIME |
| GAP-02 | TicketServiceImpl.getTicketDetail (phone strip) / assertPresignAccess | CRITICAL | DONE | TicketServiceImplTest.java | SEC-03/08 TECHNICIAN/BOARD_MEMBER phone=null |
| GAP-03 | UserServiceImpl.updateUser (role change) / UserController | HIGH | DONE | UserServiceImplTest.java | SEC-04 role promotion logging |
| GAP-04 | AmenityServiceImpl.createBooking (guards) | HIGH | DONE | AmenityControllerTest.java (3 tests added) | SEC-11/12/22 past-date/advance/duration guards |
| GAP-05 | AuthServiceImpl.logout (null/blank token) | HIGH | DONE | AuthServiceTest.java (2 tests added) | SEC-13 null guard |
| GAP-06 | ParkingController GET endpoints / ParkingServiceImpl | HIGH | DONE | ParkingControllerTest.java (4 tests added) | list slots, assignments, guests + non-ADMIN 403 |
| GAP-07 | ContractorServiceImpl lifecycle | MEDIUM | DONE | ContractorServiceImplTest.java (8 tests) | PENDING→ACTIVE/TERMINATED + 6 NOT_FOUND guards |
| GAP-08 | ResidentServiceImpl assignment guards | MEDIUM | DONE | ResidentServiceImplTest.java (7 tests) | CONFLICT(dup active/double-moveout) + NOT_FOUND(user/apt/resident) + FORBIDDEN(RESIDENT cross-record) |
| GAP-09 | MaintenanceScheduleRunner / ContractExpiryScheduler | MEDIUM | DONE | MaintenanceScheduleRunnerTest.java + ContractExpirySchedulerTest.java | scheduled job branches |
| GAP-10 | AnnouncementServiceImpl access control | MEDIUM | DONE | AnnouncementServiceImplTest.java (8 tests) | RESIDENT draft NOT_FOUND + published CONFLICT + SEC-07 markRead + scope VALIDATION_ERROR |
| GAP-11 | ApartmentServiceImpl status transitions | MEDIUM | DONE | ApartmentServiceImplTest.java (6 tests) | RESIDENT cross-apt FORBIDDEN + NOT_FOUND + dup-unit CONFLICT + active-residents CONFLICT |
| GAP-12 | Mapper classes | LOW | SKIP | | MapStruct generated; risk too low |
| GAP-13 | SecurityConfig CORS rejection | LOW | DONE | CorsIntegrationTest.java (3 tests) | evil origin 403 + no ACAO echo + allowed origin 200 |
| GAP-14 | Systemic branch gap | LOW | DONE | — | Covered by GAP-07 through GAP-13 negative-path tests |
