# (e) FE — Self-Service Profile Page ("Trang cá nhân")

**Date:** 2026-06-18 · **Branch:** deploy/local · **Phase:** (e) FRONTEND (admin portal)
**Scope:** FRONTEND ONLY. No BE, no authStore role-gate, no `homePathFor` change.
**Endpoints (all pre-existing, verified):** `GET /api/auth/me`, `PUT /api/auth/me/profile`, `PUT /api/auth/me/password`.

---

## Files changed

| File | Change |
|---|---|
| `src/types/profile.ts` | NEW — `MyProfile` type (id/email/fullName/phone/role/avatarUrl/isActive/lastLoginAt). No `any`. |
| `src/api/hooks.ts` | NEW hooks: `useMe` (GET /auth/me, typed `MyProfile`), `useUpdateOwnProfile` (PUT /auth/me/profile, invalidates `['me']`), `useChangeOwnPassword` (PUT /auth/me/password). Reuses existing `meta.successMessage` + `skipErrorToast` pattern. |
| `src/store/authStore.ts` | NEW `setUser(user)` action — replaces held user only; no token/status/role-gate change. |
| `src/pages/ProfilePage.tsx` | NEW page — three independent areas (view / update / password). |
| `src/App.tsx` | NEW route `path="profile"` guarded `RequireRole [ADMIN, BOARD_MEMBER, TECHNICIAN]`. |
| `src/components/Layout.tsx` | NEW nav item `/profile` («Trang cá nhân») visible to all three roles. |
| `src/i18n/vi.ts` | NEW `nav.profile: 'Trang cá nhân'`. |

---

## Static trace (requirements → code)

### Route reachable by all three roles, incl. TECHNICIAN
`App.tsx` — `<Route path="profile" element={<RequireRole roles={['ADMIN','BOARD_MEMBER','TECHNICIAN']}><ProfilePage /></RequireRole>} />`.
`RequireRole` admits when `roles.includes(user.role)` → all three pass, INCLUDING TECHNICIAN. **PASS.**

### Nav visible to all three roles
`Layout.tsx` NAV — `{ to: '/profile', label: t('nav.profile'), roles: ['ADMIN','BOARD_MEMBER','TECHNICIAN'] }`.
`nav.filter(n => n.roles.includes(user.role))` → shows for all three. **PASS.**

### homePathFor UNCHANGED — technician still lands /tickets
`lib/homePathFor.ts` not touched. No new index/HomeRedirect change. Adding a `path="profile"` child route does not alter the index redirect or the `*` catch-all (both still `HomeRedirect` → `homePathFor`). Technician landing remains `/tickets`. No redirect-loop: `/profile` is a concrete authorized route for technician, so `RequireRole` never bounces them off it. **PASS.**

### Phone-change confirm fires ONLY on phone change
`ProfilePage.handleProfileSubmit` — `if (me && phone.trim() !== me.phone) { setShowPhoneConfirm(true); return; }`. Email/fullName-only edits skip the gate and call `doUpdateProfile()` directly. The confirm dialog is a real overlay (z-[60], mirrors P1 ADMIN-confirm) that gates the mutation — «Xác nhận đổi» calls `doUpdateProfile`. **PASS.**

### Refetch + held-user sync after update
`useUpdateOwnProfile.onSuccess` → `qc.invalidateQueries(['me'])` (refetch view). `doUpdateProfile` then `setUser({id,phone,fullName,role,avatarUrl})` from the 200 body so sidebar/header name + login phone update. Token NOT rotated → no logout. **PASS.**

### Password section independent + secrets cleared
Separate `<form onSubmit={handlePasswordSubmit}>`, separate endpoint (`useChangeOwnPassword`), separate submit. Client-side: complexity regex + confirm-match. Wrong current pw → `WRONG_CURRENT_PASSWORD` (422) mapped inline to currentPwError; weak new pw → `PASSWORD_POLICY_VIOLATION`/`VALIDATION_ERROR` → newPwError. On success clears all three password fields; session not invalidated. **PASS.**

### Error mapping (inline VN via getVnErrorMessage)
`PHONE_ALREADY_EXISTS` → phoneError, `EMAIL_ALREADY_EXISTS` → emailError, else profileFormError. Setting own unchanged phone is a no-op confirm-skip + BE self-exclusion → succeeds. **PASS.**

---

## Verification

- `npx tsc --noEmit` → exit 0 (no type errors; no `any` in profile code).
- `npx vite build` → **built in 2.94s, 589 modules, exit 0.**
- Code review (cavecrew-reviewer over the diff) → **No issues.**

## Status

**(e) FE done — pending CTO :80 smoke.** Build + review green.
