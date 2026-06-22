# CONFLICT → Specific Code Patch — 2026-06-09

## Per-spot analysis

| # | File:line | Situation | Target code | Enum existed |
|---|-----------|-----------|-------------|-------------|
| 1 | `VehicleServiceImpl.java:106` | License plate dup on create | `LICENSE_PLATE_ALREADY_EXISTS` | N — added |
| 2 | `VehicleServiceImpl.java:166` | License plate dup on update | `LICENSE_PLATE_ALREADY_EXISTS` | N — added |
| 3 | `ParkingServiceImpl.java:121` | Slot number dup on create | `SLOT_NUMBER_ALREADY_EXISTS` | N — added |
| 4 | `TicketServiceImpl.java:591` | Rate ticket when status != DONE | `INVALID_STATUS_TRANSITION` | Y — already existed |
| 5 | `TicketServiceImpl.java:596` | Rate ticket when already rated | `TICKET_ALREADY_RATED` | N — added |
| 6 | `AnnouncementServiceImpl.java:198` | Edit published announcement | `INVALID_STATUS_TRANSITION` | Y — already existed |
| 7 | `ResidentServiceImpl.java:251` | Move-out when already moved out | `RESIDENT_ALREADY_MOVED_OUT` | N — added |

## New ErrorCode enum entries (all HttpStatus.CONFLICT / 409)

- `LICENSE_PLATE_ALREADY_EXISTS`
- `SLOT_NUMBER_ALREADY_EXISTS`
- `TICKET_ALREADY_RATED`
- `RESIDENT_ALREADY_MOVED_OUT`

## Sensitive data leakage

- VehicleServiceImpl messages previously included raw plate number — stripped.
- ParkingServiceImpl message previously included raw slot number — stripped.

## Tests

- `ResidentServiceImplTest.moveOut_alreadyMovedOut_throwsResidentAlreadyMovedOut` — updated assertion
- `AnnouncementServiceImplTest.updateAnnouncement_publishedAnnouncement_throwsInvalidStatusTransition` — updated assertion
- `VehicleControllerTest.createVehicle_duplicatePlate_returns409` — updated `$.error` assertion
- `TicketServiceImplTest.rateTicket_notDone_throwsInvalidStatusTransition` — new
- `TicketServiceImplTest.rateTicket_alreadyRated_throwsTicketAlreadyRated` — new
- `ParkingServiceImplTest.createSlot_duplicateSlotNumber_throwsSlotNumberAlreadyExists` — new (new file)

Result: 31/31 unit tests green (ResidentServiceImplTest 12, AnnouncementServiceImplTest 8, TicketServiceImplTest 10, ParkingServiceImplTest 1).

## Not fixed (GlobalExceptionHandler fallback)

`DataIntegrityViolationException → CONFLICT` in GlobalExceptionHandler intentionally unchanged — defense-in-depth for DB-layer constraint violations.
