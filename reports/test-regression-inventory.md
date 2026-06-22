# Test Regression Inventory — 2026-06-09

Run: `./mvnw test -B` with JAVA_HOME=Java 21
Result: **244 tests run, 104 FAIL, 0 errors, 140 pass**

## Root Cause

All 104 failures = phone-login migration regression.
Every failing class calls `obtainAdminToken()` or `login()` passing `admin@gemek.vn` (email string).
`LoginRequest.phone` now validated by `PhoneUtils.isValid()` → email format → 400 VALIDATION_ERROR.
`AuthControllerTest` / `UserControllerTest` variant: phone format may pass but password wrong
(`Admin@123456` from test yml vs `GemekAdmin2026` in actual DB hash).

Fix pattern (same as AmenityControllerTest fix in 2bf2fa5):
1. `ADMIN_EMAIL`/`ADMIN_PASSWORD` constant → `"0900000000"` / `"GemekAdmin2026"`
2. `login(email)` param rename → `phone`, pass `"0900000000"`
3. `CreateResidentRequest` / `CreateUserRequest` builders: add `phone = phoneFromUid(uid)`, `dateOfBirth = "1990-01-01"`, remove `email`
4. Add `phoneFromUid(String uid)` static helper (same impl as AmenityControllerTest)

## Failing Classes

| Class | Failures | HTTP got | Cause |
|-------|----------|----------|-------|
| `AmenityBookingIntegrationTest` | 4 | 400 | email sent as phone |
| `AnnouncementControllerTest` | 4 | 400 | email sent as phone |
| `AnnouncementFlowIntegrationTest` | 3 | 400 | email sent as phone |
| `ApartmentControllerTest` | 5 | 400 | email sent as phone |
| `AuthControllerTest` | 5 | 401/400 | wrong creds / email as phone |
| `BlockControllerTest` | 9 | 400 | email sent as phone |
| `ContractorControllerTest` | 5 | 400 | email sent as phone |
| `NotificationControllerTest` | 3 | 400 | email sent as phone |
| `NotificationIntegrationTest` | 3 | 400 | email sent as phone |
| `ParkingControllerTest` | 8 | 400 | email sent as phone |
| `ReportControllerTest` | 6 | 400 | email sent as phone |
| `ResidentControllerTest` | 19 | 400 | email sent as phone |
| `TicketControllerTest` | 12 | 400 | email sent as phone |
| `TicketLifecycleIntegrationTest` | 5 | 400 | email sent as phone |
| `UserControllerTest` | 4 | 401 | wrong password (test yml vs DB hash) |
| `VehicleControllerTest` | 9 | 400 | email sent as phone |

**Total: 16 classes, 104 failures. Zero failures unrelated to phone-login migration.**

## Classes Passing (unaffected)

- `AmenityControllerTest` — 15/15 (fixed in 2bf2fa5)
- `PhoneUtilsTest` — all pass
- `ContractExpirySchedulerTest` — 4/4
- `MaintenanceScheduleRunnerTest` — 5/5
- All unit/scheduler tests (no login dependency)
