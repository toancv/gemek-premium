# Step 5 — Resident Phone Normalization: Diagnosis & Fix

## Diagnosis

**ResidentServiceImpl.createResident()** built the User entity directly (lines 134–142) instead of delegating to `UserServiceImpl.createUser()`. This bypassed all phone-related guards:

| Check | UserServiceImpl | ResidentServiceImpl (before fix) |
|-------|----------------|----------------------------------|
| PhoneUtils.normalize() | ✅ line 99 | ❌ missing |
| existsByPhone uniqueness | ✅ line 103 | ❌ missing |
| existsByEmail uniqueness | ✅ | ✅ (already present) |

**Duplicate phone behavior before fix:** raw DB `uq_users_phone` violation → 500 (DataIntegrityViolationException), not 409.

**Non-canonical input (+84...):** stored verbatim, breaking login (which normalizes before lookup).

## Fix Applied

In `ResidentServiceImpl.createResident()`:
1. `PhoneUtils.normalize(req.getPhone())` — throws VALIDATION_ERROR on invalid format
2. `userRepository.existsByPhone(normalizedPhone)` → throws `PHONE_ALREADY_EXISTS` (409) on conflict
3. Email null-guard added (`req.getEmail() != null`) — email is now optional per V12 schema
4. `user.setPhone(normalizedPhone)` — stores canonical form

Order: normalize → phone-unique → email-unique → find apartment → persist. Consistent with UserServiceImpl.

## Tests (ResidentServiceImplTest — 11 tests, all pass)

New tests added:
- `createResident_nonCanonicalPhone_storesCanonical` — passes `+84900000001`, asserts saved User.phone = `0900000001` via ArgumentCaptor
- `createResident_duplicatePhone_throwsPhoneAlreadyExists` — mocks existsByPhone=true, asserts PHONE_ALREADY_EXISTS

Existing tests updated: all 3 createResident_* fixtures updated with `"0900000001"` phone (were passing null, which would now throw VALIDATION_ERROR before reaching the checked condition).

## Raw test output
See `reports/step5-resident-phone.raw.txt`
