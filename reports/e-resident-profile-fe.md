# (e) Resident FE — Profile Editing — Static Trace

**Scope:** Add edit of own `fullName`/`phone`/`email` to the existing resident `ProfilePage`. Frontend-only, resident app only. BE / admin untouched. No apartment editing.
**Branch:** `deploy/local` · base HEAD `6afe09d` · **Build:** `tsc && vite build` green (584 modules, exit 0).

## Files changed (3, resident app only)
- `frontend/apps/resident/src/api/hooks.ts` — added `useUpdateOwnProfile`.
- `frontend/apps/resident/src/pages/ProfilePage.tsx` — added edit-info form + phone-change confirm Modal; view card + change-password + logout unchanged.
- `frontend/apps/resident/src/i18n/vi.ts` — added `profile.*` edit keys.

## Trace

**Edit wired to `PUT /api/auth/me/profile` via resident patterns** ✔
`useUpdateOwnProfile` (`hooks.ts`) → `put('/auth/me/profile', { fullName, phone, email })`, `meta: { skipErrorToast: true, successMessage: 'Cập nhật thông tin thành công.' }` — mirrors `useChangePassword`. `apiClient` baseURL prefixes `/api`. Form submit → `mutateAsync`; errors caught → inline red box via `getVnErrorMessage(err.response.data.error)` (same pattern as the change-password block). `PHONE_ALREADY_EXISTS` / `EMAIL_ALREADY_EXISTS` / `VALIDATION_ERROR` all map to VN strings (`errorMessages.ts:22,23,26`).

**Invalidation uses the real `useMe` key** ✔ — key is **`['me']`** (`hooks.ts:12` `useQuery({ queryKey: ['me'], queryFn: () => get('/auth/me') })`). `useUpdateOwnProfile.onSuccess` → `qc.invalidateQueries({ queryKey: ['me'] })`. A `useEffect` keyed on `me.fullName/phone/email` re-seeds the form after refetch → displayed values update, no stale.

**Phone-change confirm via `@gemek/ui` Modal, only on phone change** ✔ — `handleSaveProfile` opens the Modal **only when** `phone.trim() !== me.phone`; unchanged phone (incl. own current phone) submits directly (BE self-excludes from uniqueness). Modal copy: `profile.phoneConfirmBody` = "Bạn sắp đổi số điện thoại đăng nhập thành {phone}…". Confirm button → `submitProfile()`. `Modal` imported from `@gemek/ui` (resident had no confirm dialog before). fullName/email-only edits never open the Modal.

**Token NOT rotated on phone change** ✔ — no logout/redirect on save; JWT subject = UUID (investigation §E). After save the page stays logged in and reflects new phone.

**Toast stays center** ✔ — relies on existing `<Toaster />` (`App.tsx:63`, default `position='center'`) via `meta.successMessage`. No new Toaster, no `top-right`.

**Apartment not editable** ✔ — edit form exposes only fullName/phone/email; me payload has no apartment fields; PUT DTO accepts only those three.

**Change-password + logout untouched** ✔ — both blocks unchanged (handlers, hook, layout identical); only refactor was sharing the `ApiError` type for the existing inline-error cast (no behaviour change).

**Typing** ✔ — `MeProfile` + `ApiError` interfaces; no `any`. Build's `tsc` passed.
