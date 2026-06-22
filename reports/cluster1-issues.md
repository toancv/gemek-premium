# Cluster-1 Post-Testing Issues — 2026-06-09

## A. Login error disappears (both apps)

**Root cause:** `apiClient` 401 response interceptor (resident `src/api/client.ts` lines 33–64,
admin same file) fires on login failure (INVALID_CREDENTIALS, status 401). No refresh token
exists (user not logged in) → `window.location.href = '/login'` → full page reload → error state
cleared before the user can read it.

**Fix:** Add URL guard at top of the 401 handler — skip retry/reload for `/auth/login` and
`/auth/refresh` endpoints. These are auth endpoints where 401 is a business-logic response, not
a token-expiry signal.

---

## B. Change-password shows wrong/generic error

**Root cause (two-part):**

1. `AuthServiceImpl.changePassword` throws `ErrorCode.INVALID_CREDENTIALS` (HTTP 401) for a
   wrong current password. 401 triggers the interceptor's refresh+retry cycle: intercept → refresh
   (succeeds, user is logged in) → retry change-password → still 401 → propagates.
   If the refresh token is expired/invalidated (e.g. after Docker rebuild + logout), the catch block
   fires `window.location.href = '/login'` — page reloads, error vanishes.

2. Even when the error DOES reach the component, `INVALID_CREDENTIALS` maps to
   "Số điện thoại hoặc mật khẩu không đúng." — login-specific message shown for wrong current
   password (confusing, wrong context).

**Fix:** New `WRONG_CURRENT_PASSWORD` ErrorCode with `HttpStatus.UNPROCESSABLE_ENTITY` (422).
422 is not caught by the 401 interceptor → error goes directly to the component with the correct
code. Map to "Mật khẩu hiện tại không đúng." in `getVnErrorMessage`.

---

## C. Change-password success: no toast

**Root cause:** B and C share root cause — the mutation is failing (wrong password, interceptor
reload). Additionally, `useChangePassword` lacks `skipSuccessToast: true`: on actual success,
MutationCache fires `"Thao tác thành công"` AND component fires `"Đổi mật khẩu thành công."` —
double toast. After fixing B, the success path works; `skipSuccessToast: true` prevents the
duplicate toast.

---

## Not unexpected

All three causes are as suspected in the task description. Proceeding with fixes.
