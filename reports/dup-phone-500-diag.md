# Diagnosis: Duplicate Phone → 500 Instead of 409

**Date:** 2026-06-08

## Reproduction

| Input | Path | Before fix | After fix |
|-------|------|-----------|-----------|
| canonical dup (`0901100001`) | POST /api/residents | 500 | 409 |
| non-canonical dup (`+84901100001`) | POST /api/residents | 500 | 409 |
| canonical dup via POST /api/users | POST /api/users | 409 ✅ (service guard works) | — |

**Note:** There is no `UsersPage.tsx` in the admin frontend. Admins create users exclusively through the resident creation form → POST /api/residents.

## Root Cause

**Primary:** The Docker backend container was running pre-step-5 code (before commit 4cf2ce1). `ResidentServiceImpl.createResident()` lacked the `PhoneUtils.normalize() + existsByPhone` guard. A duplicate phone bypassed the service layer, hit the `uq_users_phone` DB constraint, and threw `DataIntegrityViolationException`. `GlobalExceptionHandler` had no handler for this exception type — it fell to the catch-all `Exception` handler → **500 INTERNAL_ERROR**.

**Secondary / defense-in-depth gap:** `GlobalExceptionHandler` lacked a `DataIntegrityViolationException` handler. Even after the service guard is live, any race condition (two concurrent creates with the same phone both passing `existsByPhone` under READ_COMMITTED isolation) would still produce a 500.

**FE contributing issue:** `useCreateResident` has `skipErrorToast: true`, so the MutationCache global toast never fires. The inline `catch` in `ResidentsPage.handleCreate()` does show `setFormError(...)`, but the 409 branch showed a hardcoded wrong message: `"Email đã được sử dụng."` — regardless of whether the conflict was phone or email.

## Fixes Applied

1. **`GlobalExceptionHandler.java`** — added `@ExceptionHandler(DataIntegrityViolationException.class)` → 409 CONFLICT. Import: `org.springframework.dao.DataIntegrityViolationException`.
2. **Docker rebuild** — `docker compose up -d --build backend` — deploys step-5 guard (existsByPhone in ResidentServiceImpl) into the running container.
3. **`ResidentsPage.tsx` line 112** — 409 branch now uses `err?.response?.data?.message` (server message) instead of hardcoded `"Email đã được sử dụng."`.

## FE Toast Note (separate issue, not fixed here)

`useCreateResident` has `meta: { skipErrorToast: true }`. This means the MutationCache `onError` global toast is suppressed for all errors from this mutation. Errors surface only through the inline `setFormError()`. This is intentional (form owns the error UX), but means phone-dup errors will appear as an inline form message, not a toast. If a toast is desired, `skipErrorToast` should be removed from `useCreateResident`.

## Test Coverage

- `GlobalExceptionHandlerTest` (new): 2 tests — `DataIntegrityViolationException` → 409, error code = CONFLICT
- `ResidentServiceImplTest` (existing): 12 tests including `createResident_duplicatePhone_throwsPhoneAlreadyExists` and `createResident_nonCanonicalPhone_storesCanonical`
- All 14 tests green.
