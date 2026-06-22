# (e) Resident Profile Editing — READ-ONLY Investigation

**Scope:** Add profile editing to the **resident app (mobile)** — view + edit own `fullName`/`phone`/`email` + change own password. Apartment info view-only or omitted (CTO ruling).
**Mode:** Read-only. No code modified, no code committed.
**Date:** 2026-06-18 · **Branch:** `deploy/local` · **HEAD:** `525b469`

---

## Tree state
`git status --short` shows only untracked `reports/*` and `scripts/GenHash.java` — **no tracked code modified**. Clean for code. ✔

---

## §A — Endpoints usable by RESIDENT

All three `/api/auth/me*` endpoints carry **no `@PreAuthorize`** and fall under the single catch-all gate
`SecurityConfig.java:118` → `.anyRequest().authenticated()`. Authentication-only, **not role-gated** → a RESIDENT JWT passes.

| Endpoint | Usable by RESIDENT | Evidence | Gate |
|----------|:--:|----------|------|
| `GET /api/auth/me` | **YES** | `AuthController.java:174-178` (`getMe`, no `@PreAuthorize`) | `SecurityConfig.java:118` |
| `PUT /api/auth/me/password` | **YES** | `AuthController.java:187-194` (`changePassword`, no `@PreAuthorize`) | `SecurityConfig.java:118` |
| `PUT /api/auth/me/profile` | **YES** | `AuthController.java:207-213` (`updateOwnProfile`, no `@PreAuthorize`); javadoc `:199` states *"Authenticated-only (any role) via `anyRequest().authenticated()` — no `@PreAuthorize`"* | `SecurityConfig.java:118` |

**No BE change needed.** None of the three is admin-role-gated away from RESIDENT.

---

## §B — `GET /api/auth/me` payload + view-only fields

Returns `UserDetailResponse` (`UserDetailResponse.java:29-40`):

| Field | Self-editable? |
|-------|----------------|
| `fullName` | **editable** (sent to PUT) |
| `phone` | **editable** (sent to PUT — also login id, see §E) |
| `email` | **editable** (sent to PUT) |
| `id`, `role`, `dateOfBirth`, `avatarUrl`, `isActive`, `lastLoginAt`, `createdAt` | view-only |

**No apartment / unit / «Tòa» fields exist in the me payload.** There is therefore nothing apartment-shaped to render view-only on this page from `me`. (Apartment/unit data lives behind a separate endpoint `GET /residents/me` — resident hook `useMyResident`, `hooks.ts:17` — which is **out of scope** per the CTO ruling: apartment is view-only-or-omitted, never self-editable.)

The PUT body `UpdateOwnProfileRequest` (`UpdateOwnProfileRequest.java:26-39`) accepts **only** `fullName`/`phone`/`email`; it deliberately omits `role`/`isActive`/`password`/`id` (privilege-escalation guard, DTO javadoc `:14-20`), and being a record it silently drops any extra JSON keys. The FE edit form must **not** surface apartment as editable (and cannot send it anyway).

---

## §C — Resident nav slot for the page

**A profile page already exists and is wired.** This is an *enhancement of an existing page*, not a new page or new nav entry.

- **Route:** `App.tsx:58` → `<Route path="profile" element={<ProfilePage />} />`
- **Nav entry:** bottom-tab bar, `Layout.tsx:45` → `{ to: '/profile', label: t('nav.profile'), icon: 'Me' }`; label `'Cá nhân'` (`vi.ts:18`). Resident nav = **fixed bottom tab bar** (`Layout.tsx:95-104`), 5 tabs: Home / Phản ánh / Phương tiện / Tin tức / Cá nhân.
- **Current `ProfilePage.tsx` (1-77):** view-only profile card (`fullName`, `phone`, `role`, `email`, `lastLoginAt` — lines 36-52) + change-password form (54-69, uses `useChangePassword`) + logout button (72-74).
- **Gap:** the page currently has **no edit** of `fullName`/`phone`/`email` and **no `useUpdateOwnProfile`** wiring. That is the FE work.

---

## §D — Resident-app patterns the FE phase MUST reuse

| Concern | Resident pattern (reuse this) | Reference |
|---------|-------------------------------|-----------|
| **Inline form error** | Local state + `getVnErrorMessage(err.response.data.error)` rendered in a red box `text-red-600 bg-red-50` | `ProfilePage.tsx:15,27,64` |
| **Mutation hook shape** | `useMutation({ mutationFn: put(...), meta: { skipErrorToast: true, successMessage: '…' } })` — error handled inline, success → toast | `hooks.ts:111-112` (change-password) |
| **Global toast wiring** | `MutationCache(mutationCacheHandlers)` from `@gemek/ui`; default success/error toasts, opt-out via `meta.skipErrorToast`/`skipSuccessToast`, custom text via `meta.successMessage` | `main.tsx:12`; `mutationToast.ts:3-28` |
| **Toast position (mobile)** | `<Toaster />` with **default `position='center'`** = top-center `top-4 left-1/2 -translate-x-1/2` (admin uses `top-right`; resident must stay center) | `App.tsx:63`; `Toast.tsx:19-26` |
| **i18n** | `t()` = `createT(vi, viShared)` — app dict first, then `@gemek/ui` shared; `profile.*` keys live at `vi.ts:102-112`. Add new edit keys here (e.g. `fullName`, `email`, `save`, `saving`, phone-change confirm copy). | `vi.ts:9,102-116` |
| **Confirm dialog** | **None in the resident app** (grep for `confirm`/`Dialog`/`Modal`/`window.confirm` → 0 hits). `@gemek/ui` exports a `Modal` component (`index.ts:6`) that the resident app does not yet import. For the phone-change confirmation (§E), reuse `@gemek/ui` `Modal` for consistency. | `index.ts:6` |

> Do **not** copy admin patterns (admin uses `top-right` toast, a typed `useUpdateOwnProfile` in `admin/.../hooks.ts`, and `types/profile.ts`). Mirror them conceptually but wire into the resident dict/Toaster/Modal.

---

## §E — Resident-specific notes on phone/email change

- **Phone is the login identifier for every role**, including RESIDENT: `SecurityConfig.java:149` → `userDetailsService` resolves `userRepository.findByPhone(username)`. ⇒ the **phone-change confirmation applies to resident** exactly as it does to admin (changing phone changes the login id).
- **Token survives a phone change:** JWT subject = user **UUID**, not phone — confirmed by the BE integration test (`SelfProfileUpdateIntegrationTest.java:119` *"same token still valid — subject is UUID"*). Changing phone via PUT does **not** invalidate the current access token; no re-login required.
- **Email** is informational only (not a login id), unique when provided, blank treated as null (`UpdateOwnProfileRequest.java:36-38`). No confirm needed for email change.

---

## Verdict — **FE-ONLY. No backend change.**

All three `/api/auth/me*` endpoints already accept a RESIDENT token; the me payload exposes the editable trio and contains no apartment fields; the profile page, route, and nav tab already exist. Backend is complete for this feature.

### FE phase breakdown (resident app only)
1. **Hook** — add `useUpdateOwnProfile` to `resident/src/api/hooks.ts`: `put('/auth/me/profile', data)`, `meta: { skipErrorToast: true, successMessage: 'Cập nhật thông tin thành công.' }`, `onSuccess → invalidateQueries(['me'])`. Mirror `useChangePassword` (`hooks.ts:111-112`).
2. **ProfilePage edit** — add an edit form (or edit mode) for `fullName`/`phone`/`email`, seeded from `useMe()` data; inline error via `getVnErrorMessage`, mirroring the change-password block (`ProfilePage.tsx:54-69`). Apartment must **not** appear as editable.
3. **Phone-change confirm** — when submitted `phone !== me.phone`, gate the save behind a `@gemek/ui` `Modal` confirm ("phone is your login id"). Phone unchanged → submit directly.
4. **i18n** — add the new `profile.*` keys (field labels, save/saving, phone-change confirm copy) to `vi.ts:102-112`.
5. **Toast** — rely on the existing `meta.successMessage` → center `<Toaster />`; do not introduce `top-right`.

**Out of scope (CTO ruling):** apartment/unit/«Tòa» editing. Optional view-only apartment block (from `GET /residents/me`) is *not* required and not part of this scope.

---

*Investigation complete — awaiting CTO ruling. No code implemented.*
